/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package zipkin2.elasticsearch;

import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

import java.io.IOException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpMethod;

import zipkin2.elasticsearch.internal.client.HttpCall;

/**
 * Base version for both Elasticsearch and OpenSearch distributions.
 */
public abstract class BaseVersion {
  final int major, minor;

  BaseVersion(int major, int minor) {
    this.major = major;
    this.minor = minor;
  }

  /**
   * Gets the version for particular distribution, returns either {@link ElasticsearchVersion}
   * or {@link OpensearchVersion} instance.
   * @param http the HTTP client
   * @return either {@link ElasticsearchVersion} or {@link OpensearchVersion} instance
   * @throws IOException in case of I/O errors
   */
  static BaseVersion get(HttpCall.Factory http) throws IOException {
    return Parser.INSTANCE.get(http);
  }

  /**
   * Does this version of Elasticsearch / OpenSearch still support mapping types?
   * @return "true" if mapping types are supported, "false" otherwise
   */
  public abstract boolean supportsTypes();
  
  enum Parser implements HttpCall.BodyConverter<BaseVersion> {
    INSTANCE;

    final Pattern REGEX = Pattern.compile("(\\d+)\\.(\\d+).*");

    BaseVersion get(HttpCall.Factory callFactory) throws IOException {
      AggregatedHttpRequest getNode = AggregatedHttpRequest.of(HttpMethod.GET, "/");
      BaseVersion version = callFactory.newCall(getNode, this, "get-node").execute();
      if (version == null) {
        throw new IllegalArgumentException("No content reading Elasticsearch/OpenSearch version");
      }
      return version;
    }

    @Override
    public BaseVersion convert(JsonParser parser, Supplier<String> contentString) {
      String version = null;
      String distribution = null;
      try {
        if (enterPath(parser, "version") != null) {
          while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
              if (parser.currentName() == "distribution") {
                distribution = parser.getText();
              } else if (parser.currentName() == "number") {
                version = parser.getText();
              }
            }
          }
        }
      } catch (RuntimeException | IOException possiblyParseException) {
        // EmptyCatch ignored
      }

      if (version == null) {
        throw new IllegalArgumentException(
          ".version.number not found in response: " + contentString.get());
      }

      Matcher matcher = REGEX.matcher(version);
      if (!matcher.matches()) {
        throw new IllegalArgumentException("Invalid .version.number: " + version);
      }

      try {
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        if ("opensearch".equalsIgnoreCase(distribution)) {
          return new OpensearchVersion(major, minor);
        } else {
          return new ElasticsearchVersion(major, minor);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid .version.number: " + version
          + ", for .version.distribution:" + distribution);
      }
    }
  }
}
