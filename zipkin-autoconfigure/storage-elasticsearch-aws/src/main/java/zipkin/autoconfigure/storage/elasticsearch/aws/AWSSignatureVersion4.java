/**
 * Copyright 2015-2016 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.autoconfigure.storage.elasticsearch.aws;

import com.squareup.moshi.JsonReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static java.lang.String.format;
import static zipkin.internal.Util.checkNotNull;
import static zipkin.moshi.JsonReaders.enterPath;

// http://docs.aws.amazon.com/general/latest/gr/signature-version-4.html
final class AWSSignatureVersion4 implements Interceptor {
  static final String EMPTY_STRING_HASH =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  static final String HOST = "host";
  static final String X_AMZ_DATE = "x-amz-date";
  static final String X_AMZ_SECURITY_TOKEN = "x-amz-security-token";
  static final String[] CANONICAL_HEADERS = {HOST, X_AMZ_DATE, X_AMZ_SECURITY_TOKEN};
  static final String HOST_DATE = HOST + ";" + X_AMZ_DATE;
  static final String HOST_DATE_TOKEN = HOST_DATE + ";" + X_AMZ_SECURITY_TOKEN;

  // SimpleDateFormat isn't thread-safe
  static final ThreadLocal<SimpleDateFormat> iso8601 = new ThreadLocal<SimpleDateFormat>() {
    @Override protected SimpleDateFormat initialValue() {
      SimpleDateFormat result = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
      result.setTimeZone(TimeZone.getTimeZone("UTC"));
      return result;
    }
  };

  final String region;
  final String service;
  final AWSCredentials.Provider credentials;

  AWSSignatureVersion4(String region, String service, AWSCredentials.Provider credentials) {
    this.region = checkNotNull(region, "region");
    this.service = checkNotNull(service, "service");
    this.credentials = checkNotNull(credentials, "credentials");
  }

  @Override public Response intercept(Chain chain) throws IOException {
    Request input = chain.request();
    Request signed = sign(input);
    Response response = chain.proceed(signed);
    if (response.code() == 403) {
      try (ResponseBody body = response.body()) {
        JsonReader message = enterPath(JsonReader.of(body.source()), "message");
        if (message != null) throw new IllegalStateException(message.nextString());
      }
      throw new IllegalStateException(response.toString());
    }
    return response;
  }

  static Buffer canonicalString(Request input) throws IOException {
    Buffer result = new Buffer();
    // HTTPRequestMethod + '\n' +
    result.writeUtf8(input.method()).writeByte('\n');

    // CanonicalURI + '\n' +
    // TODO: make this more efficient
    result.writeUtf8(input.url().encodedPath()
        .replace("*", "%2A").replace(",", "%2C")
    ).writeByte('\n');

    // CanonicalQueryString + '\n' +
    String query = input.url().encodedQuery();
    result.writeUtf8(query == null ? "" : query).writeByte('\n');

    // CanonicalHeaders + '\n' +
    Buffer signedHeaders = new Buffer();
    for (String canonicalHeader: CANONICAL_HEADERS) {
      String value = input.header(canonicalHeader);
      if (value != null) {
        result.writeUtf8(canonicalHeader).writeByte(':').writeUtf8(value).writeByte('\n');
        signedHeaders.writeByte(';').writeUtf8(canonicalHeader);
      }
    }
    result.writeByte('\n'); // end headers

    // SignedHeaders + '\n' +
    signedHeaders.readByte(); // throw away the first semicolon
    result.writeAll(signedHeaders);
    result.writeByte('\n');

    // HexEncode(Hash(Payload))
    if (input.body() != null && input.body().contentLength() != 0) {
      Buffer body = new Buffer();
      input.body().writeTo(body);
      result.writeUtf8(body.sha256().hex());
    } else {
      result.writeUtf8(EMPTY_STRING_HASH);
    }
    return result;
  }

  static Buffer toSign(String timestamp, String credentialScope, Buffer canonicalRequest) {
    Buffer result = new Buffer();
    // Algorithm + '\n' +
    result.writeUtf8("AWS4-HMAC-SHA256\n");
    // RequestDate + '\n' +
    result.writeUtf8(timestamp).writeByte('\n');
    // CredentialScope + '\n' +
    result.writeUtf8(credentialScope).writeByte('\n');
    // HexEncode(Hash(CanonicalRequest))
    result.writeUtf8(canonicalRequest.sha256().hex());
    return result;
  }

  Request sign(Request input) throws IOException {
    AWSCredentials credentials = checkNotNull(this.credentials.get(), "awsCredentials");

    String timestamp = iso8601.get().format(new Date());
    String yyyyMMdd = timestamp.substring(0, 8);

    String credentialScope = format("%s/%s/%s/%s", yyyyMMdd, region, service, "aws4_request");

    Request.Builder builder = input.newBuilder();
    builder.header(HOST, input.url().host());
    builder.header(X_AMZ_DATE, timestamp);
    if (credentials.sessionToken != null) {
      builder.header(X_AMZ_SECURITY_TOKEN, credentials.sessionToken);
    }

    Buffer canonicalString = canonicalString(builder.build());
    String signedHeaders = credentials.sessionToken == null ? HOST_DATE : HOST_DATE_TOKEN;

    Buffer toSign = toSign(timestamp, credentialScope, canonicalString);

    // TODO: this key is invalid when the secret key or the date change. both are very infrequent
    byte[] signatureKey = signatureKey(credentials.secretKey, yyyyMMdd);
    String signature = hex(hmacSHA256(toSign.readByteArray(), signatureKey));

    String authorization = new StringBuilder().append("AWS4-HMAC-SHA256 Credential=")
        .append(credentials.accessKey).append('/').append(credentialScope)
        .append(", SignedHeaders=").append(signedHeaders)
        .append(", Signature=").append(signature).toString();

    return builder.header("authorization", authorization).build();
  }

  byte[] signatureKey(String secretKey, String yyyyMMdd) {
    byte[] kSecret = asciiToByteArray("AWS4" + secretKey);
    byte[] kDate = hmacSHA256(asciiToByteArray(yyyyMMdd), kSecret);
    byte[] kRegion = hmacSHA256(asciiToByteArray(region), kDate);
    byte[] kService = hmacSHA256(asciiToByteArray(service), kRegion);
    byte[] kSigning = hmacSHA256(asciiToByteArray("aws4_request"), kService);
    return kSigning;
  }

  byte[] hmacSHA256(byte[] data, byte[] key) {
    try {
      String algorithm = "HmacSHA256";
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key, algorithm));
      return mac.doFinal(data);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }

  byte[] asciiToByteArray(String ascii) {
    int length = ascii.length();
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = (byte) ascii.charAt(i);
    }
    return result;
  }

  static final char[] HEX_DIGITS =
      {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  static String hex(byte[] v) {
    char[] data = new char[v.length * 2];
    int i = 0;
    for (byte b : v) {
      data[i++] = HEX_DIGITS[(b >> 4) & 0xf];
      data[i++] = HEX_DIGITS[b & 0xf];
    }
    return new String(data);
  }
}
