/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin;

/**
 * Well-known {@link BinaryAnnotation#key binary annotation aka Tag keys}.
 *
 * <h3>Overhead of adding Trace Data</h3>
 *
 * Overhead is directly related to the size of trace data exported out of process. Accordingly, it
 * is better to tag what's important for latency troubleshooting, i.e. a whitelist vs. collecting
 * everything and filtering downstream. The keys listed here are very common in tracing tools, and
 * are considerate to the issue of overhead.
 *
 * <p> When evaluating new keys, consider how much additional data it implies, and if that data is
 * critical to classifying, filtering or displaying traces. More data often means larger systems,
 * less retention, or a lower sample rate.
 *
 * <p> For example, in zipkin, a thrift-encoded span with an "sr" annotation is 82 bytes plus the
 * size of its name and associated service. The maximum size of an HTTP cookie is 4096 bytes,
 * roughly 50x that. Even if compression helps, if you aren't analyzing based on cookies, storing
 * them displaces resources that could be used for more traces. Meanwhile, you have another system
 * storing private data! The takeaway isn't never store cookies, as there are valid cases for this.
 * The takeaway is to be conscious about what's you are storing.
 */
public final class TraceKeys {

  /**
   * The domain portion of the URL or host header. Ex. "mybucket.s3.amazonaws.com"
   *
   * <p>Used to filter by host as opposed to ip address.
   */
  public static final String HTTP_HOST = "http.host";

  /**
   * The HTTP method, or verb, such as "GET" or "POST".
   *
   * <p>Used to filter against an http route.
   */
  public static final String HTTP_METHOD = "http.method";

  /**
   * The absolute http path, without any query parameters. Ex. "/objects/abcd-ff"
   *
   * <p>Used as a filter or to clarify the request path for a given route. For example, the path for
   * a route "/objects/:objectId" could be "/objects/abdc-ff". This does not limit cardinality like
   * {@link #HTTP_ROUTE} can, so is not a good input to a span name.
   *
   * <p>The Zipkin query api only supports equals filters. Dropping query parameters makes the
   * number of distinct URIs less. For example, one can query for the same resource, regardless of
   * signing parameters encoded in the query line. Dropping query parameters also limits the
   * security impact of this tag.
   *
   * <p>Historical note: This was commonly expressed as "http.uri" in zipkin, even though it was most
   */
  public static final String HTTP_PATH = "http.path";

  /**
   * The route which a request matched or "" (empty string) if routing is supported, but there was
   * no match. Ex "/objects/{objectId}"
   *
   * <p>Often used as a span name when known, with empty routes coercing to "not_found" or
   * "redirected" based on {@link #HTTP_STATUS_CODE}.
   *
   * <p>Unlike {@link #HTTP_PATH}, this value is fixed cardinality, so is a safe input to a span
   * name function or a metrics dimension. Different formats are possible. For example, the
   * following are all valid route templates: "/objects" "/objects/:objectId" "/objects/*"
   */
  public static final String HTTP_ROUTE = "http.route";

  /**
   * The entire URL, including the scheme, host and query parameters if available. Ex.
   * "https://mybucket.s3.amazonaws.com/objects/abcd-ff?X-Amz-Algorithm=AWS4-HMAC-SHA256..."
   *
   * <p>Combined with {@linkplain #HTTP_METHOD}, you can understand the fully-qualified request
   * line.
   *
   * <p>This is optional as it may include private data or be of considerable length.
   */
  public static final String HTTP_URL = "http.url";

  /**
   * The HTTP status code, when not in 2xx range. Ex. "503"
   *
   * <p>Used to filter for error status.
   */
  public static final String HTTP_STATUS_CODE = "http.status_code";

  /**
   * The size of the non-empty HTTP request body, in bytes. Ex. "16384"
   *
   * <p>Large uploads can exceed limits or contribute directly to latency.
   */
  public static final String HTTP_REQUEST_SIZE = "http.request.size";

  /**
   * The size of the non-empty HTTP response body, in bytes. Ex. "16384"
   *
   * <p>Large downloads can exceed limits or contribute directly to latency.
   */
  public static final String HTTP_RESPONSE_SIZE = "http.response.size";

  /**
   * The query executed for SQL call.  Ex. "select * from customers where id = ?"
   *
   * <p>Used to understand the complexity of a request
   */
  public static final String SQL_QUERY = "sql.query";

  private TraceKeys() {
  }
}
