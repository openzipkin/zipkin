/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import zipkin.storage.QueryRequest;

public final class Constants {

  /**
   * The client sent ("cs") a request to a server. There is only one send per span. For example, if
   * there's a transport error, each attempt can be logged as a {@link #WIRE_SEND} annotation.
   *
   * <p>If chunking is involved, each chunk could be logged as a separate {@link
   * #CLIENT_SEND_FRAGMENT} in the same span.
   *
   * <p>{@link Annotation#endpoint} is not the server. It is the host which logged the send event,
   * almost always the client. When logging CLIENT_SEND, instrumentation should also log the {@link
   * #SERVER_ADDR}.
   */
  public static final String CLIENT_SEND = "cs";

  /**
   * The client received ("cr") a response from a server. There is only one receive per span. For
   * example, if duplicate responses were received, each can be logged as a {@link #WIRE_RECV}
   * annotation.
   *
   * <p>If chunking is involved, each chunk could be logged as a separate {@link
   * #CLIENT_RECV_FRAGMENT} in the same span.
   *
   * <p>{@link Annotation#endpoint} is not the server. It is the host which logged the receive
   * event, almost always the client. The actual endpoint of the server is recorded separately as
   * {@link #SERVER_ADDR} when {@link #CLIENT_SEND} is logged.
   */
  public static final String CLIENT_RECV = "cr";

  /**
   * The server sent ("ss") a response to a client. There is only one response per span. If there's
   * a transport error, each attempt can be logged as a {@link #WIRE_SEND} annotation.
   *
   * <p>Typically, a trace ends with a server send, so the last timestamp of a trace is often the
   * timestamp of the root span's server send.
   *
   * <p>If chunking is involved, each chunk could be logged as a separate {@link
   * #SERVER_SEND_FRAGMENT} in the same span.
   *
   * <p>{@link Annotation#endpoint} is not the client. It is the host which logged the send event,
   * almost always the server. The actual endpoint of the client is recorded separately as {@link
   * #CLIENT_ADDR} when {@link #SERVER_RECV} is logged.
   */
  public static final String SERVER_SEND = "ss";

  /**
   * The server received ("sr") a request from a client. There is only one request per span.  For
   * example, if duplicate responses were received, each can be logged as a {@link #WIRE_RECV}
   * annotation.
   *
   * <p>Typically, a trace starts with a server receive, so the first timestamp of a trace is often
   * the timestamp of the root span's server receive.
   *
   * <p>If chunking is involved, each chunk could be logged as a separate {@link
   * #SERVER_RECV_FRAGMENT} in the same span.
   *
   * <p>{@link Annotation#endpoint} is not the client. It is the host which logged the receive
   * event, almost always the server. When logging SERVER_RECV, instrumentation should also log the
   * {@link #CLIENT_ADDR}.
   */
  public static final String SERVER_RECV = "sr";

  /**
   * Message send ("ms") is a request to send a message to a destination, usually a broker. This may
   * be the only annotation in a messaging span. If {@link #WIRE_SEND} exists in the same span,it
   * follows this moment and clarifies delays sending the message, such as batching.
   *
   * <p>Unlike RPC annotations like {@link #CLIENT_SEND}, messaging spans never share a span ID. For
   * example, "ms" should always be the parent of "mr".
   *
   * <p>{@link Annotation#endpoint} is not the destination, it is the host which logged the send
   * event: the producer. When annotating MESSAGE_SEND, instrumentation should also tag the {@link
   * #MESSAGE_ADDR}.
   */
  public static final String MESSAGE_SEND = "ms";

  /**
   * A consumer received ("mr") a message from a broker. This may be the only annotation in a
   * messaging span. If {@link #WIRE_RECV} exists in the same span, it precedes this moment and
   * clarifies any local queuing delay.
   *
   * <p>Unlike RPC annotations like {@link #SERVER_RECV}, messaging spans never share a span ID. For
   * example, "mr" should always be a child of "ms" unless it is a root span.
   *
   * <p>{@link Annotation#endpoint} is not the broker, it is the host which logged the receive
   * event: the consumer.  When annotating MESSAGE_RECV, instrumentation should also tag the {@link
   * #MESSAGE_ADDR}.
   */
  public static final String MESSAGE_RECV = "mr";

  /**
   * Optionally logs an attempt to send a message on the wire. Multiple wire send events could
   * indicate network retries. A lag between client or server send and wire send might indicate
   * queuing or processing delay.
   */
  public static final String WIRE_SEND = "ws";

  /**
   * Optionally logs an attempt to receive a message from the wire. Multiple wire receive events
   * could indicate network retries. A lag between wire receive and client or server receive might
   * indicate queuing or processing delay.
   */
  public static final String WIRE_RECV = "wr";

  /**
   * Optionally logs progress of a ({@linkplain #CLIENT_SEND}, {@linkplain #WIRE_SEND}). For
   * example, this could be one chunk in a chunked request.
   */
  public static final String CLIENT_SEND_FRAGMENT = "csf";

  /**
   * Optionally logs progress of a ({@linkplain #CLIENT_RECV}, {@linkplain #WIRE_RECV}). For
   * example, this could be one chunk in a chunked response.
   */
  public static final String CLIENT_RECV_FRAGMENT = "crf";

  /**
   * Optionally logs progress of a ({@linkplain #SERVER_SEND}, {@linkplain #WIRE_SEND}). For
   * example, this could be one chunk in a chunked response.
   */
  public static final String SERVER_SEND_FRAGMENT = "ssf";

  /**
   * Optionally logs progress of a ({@linkplain #SERVER_RECV}, {@linkplain #WIRE_RECV}). For
   * example, this could be one chunk in a chunked request.
   */
  public static final String SERVER_RECV_FRAGMENT = "srf";

  /**
   * The {@link BinaryAnnotation#value value} of "lc" is the component or namespace of a local
   * span.
   *
   * <p>{@link BinaryAnnotation#endpoint} adds service context needed to support queries.
   *
   * <p>Local Component("lc") supports three key features: flagging, query by service and filtering
   * Span.name by namespace.
   *
   * <p>While structurally the same, local spans are fundamentally different than RPC spans in how
   * they should be interpreted. For example, zipkin v1 tools center on RPC latency and service
   * graphs. Root local-spans are neither indicative of critical path RPC latency, nor have impact
   * on the shape of a service graph. By flagging with "lc", tools can special-case local spans.
   *
   * <p>Zipkin v1 Spans are unqueryable unless they can be indexed by service name. The only path
   * to a {@link Endpoint#serviceName service name} is via {@link BinaryAnnotation#endpoint
   * host}. By logging "lc", a local span can be queried even if no other annotations are logged.
   *
   * <p>The value of "lc" is the namespace of {@link Span#name}. For example, it might be
   * "finatra2", for a span named "bootstrap". "lc" allows you to resolves conflicts for the same
   * Span.name, for example "finatra/bootstrap" vs "finch/bootstrap". Using local component, you'd
   * search for spans named "bootstrap" where "lc=finch"
   */
  public static final String LOCAL_COMPONENT = "lc";

  /**
   * When an {@link Annotation#value}, this indicates when an error occurred. When a {@link
   * BinaryAnnotation#key}, the value is a human readable message associated with an error.
   *
   * <p>Due to transient errors, an ERROR annotation should not be interpreted as a span failure,
   * even the annotation might explain additional latency. Instrumentation should add the ERROR
   * binary annotation when the operation failed and couldn't be recovered.
   *
   * <p>Here's an example: A span has an ERROR annotation, added when a WIRE_SEND failed. Another
   * WIRE_SEND succeeded, so there's no ERROR binary annotation on the span because the overall
   * operation succeeded.
   *
   * <p>Note that RPC spans often include both client and server hosts: It is possible that only one
   * side perceived the error.
   *
   * @since Zipkin 1.3
   */
  public static final String ERROR = "error";

  /**
   * When present, {@link BinaryAnnotation#endpoint} indicates a client address ("ca") in a span.
   * Most likely, there's only one. Multiple addresses are possible when a client changes its ip or
   * port within a span.
   */
  public static final String CLIENT_ADDR = "ca";

  /**
   * When present, {@link BinaryAnnotation#endpoint} indicates a server address ("sa") in a span.
   * Most likely, there's only one. Multiple addresses are possible when a client is redirected, or
   * fails to a different server ip or port.
   */
  public static final String SERVER_ADDR = "sa";

  /** Indicates the remote address of a messaging span, usually the broker. */
  public static final String MESSAGE_ADDR = "ma";

  /**
   * Zipkin's core annotations indicate when a client or server operation began or ended.
   *
   * <p>These annotations are used to derive span timestamps and durations or highlight common
   * latency explaining events. However, they aren't intuitive as {@link QueryRequest storage
   * queries}, so needn't be indexed.
   */
  public static final List<String> CORE_ANNOTATIONS = Collections.unmodifiableList(
      Arrays.asList(CLIENT_SEND, CLIENT_RECV, SERVER_SEND, SERVER_RECV, WIRE_SEND, WIRE_RECV,
          CLIENT_SEND_FRAGMENT, CLIENT_RECV_FRAGMENT, SERVER_SEND_FRAGMENT, SERVER_RECV_FRAGMENT));

  private Constants() {
  }
}
