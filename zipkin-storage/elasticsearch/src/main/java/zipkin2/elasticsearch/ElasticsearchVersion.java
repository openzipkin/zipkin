/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import com.fasterxml.jackson.core.JsonParser;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpMethod;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import zipkin2.elasticsearch.internal.client.HttpCall;

import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

/** Helps avoid problems comparing versions by number. Ex 7.10 should be > 7.9 */
public final class ElasticsearchVersion implements Comparable<ElasticsearchVersion> {
  public static final ElasticsearchVersion V5_0 = new ElasticsearchVersion(5, 0);
  public static final ElasticsearchVersion V6_0 = new ElasticsearchVersion(6, 0);
  public static final ElasticsearchVersion V6_7 = new ElasticsearchVersion(6, 7);
  public static final ElasticsearchVersion V7_0 = new ElasticsearchVersion(7, 0);
  public static final ElasticsearchVersion V7_8 = new ElasticsearchVersion(7, 8);
  public static final ElasticsearchVersion V9_0 = new ElasticsearchVersion(9, 0);

  static ElasticsearchVersion get(HttpCall.Factory http) throws IOException {
    return Parser.INSTANCE.get(http);
  }

  final int major, minor;

  ElasticsearchVersion(int major, int minor) {
    this.major = major;
    this.minor = minor;
  }

  @Override public int compareTo(ElasticsearchVersion other) {
    if (major < other.major) return -1;
    if (major > other.major) return 1;
    return Integer.compare(minor, other.minor);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof ElasticsearchVersion)) return false;
    ElasticsearchVersion that = (ElasticsearchVersion) o;
    return this.major == that.major && this.minor == that.minor;
  }

  @Override public int hashCode() {
    return Objects.hash(major, minor);
  }

  @Override public String toString() {
    return major + "." + minor;
  }

  enum Parser implements HttpCall.BodyConverter<ElasticsearchVersion> {
    INSTANCE;

    final Pattern REGEX = Pattern.compile("(\\d+)\\.(\\d+).*");

    ElasticsearchVersion get(HttpCall.Factory callFactory) throws IOException {
      AggregatedHttpRequest getNode = AggregatedHttpRequest.of(HttpMethod.GET, "/");
      ElasticsearchVersion version = callFactory.newCall(getNode, this, "get-node").execute();
      if (version == null) {
        throw new IllegalArgumentException("No content reading Elasticsearch version");
      }
      return version;
    }

    @Override
    public ElasticsearchVersion convert(JsonParser parser, Supplier<String> contentString) {
      String version = null;
      try {
        if (enterPath(parser, "version", "number") != null) version = parser.getText();
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
        return new ElasticsearchVersion(major, minor);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid .version.number: " + version);
      }
    }
  }
}
