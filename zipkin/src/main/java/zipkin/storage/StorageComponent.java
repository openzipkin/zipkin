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
package zipkin.storage;

import zipkin.Component;
import zipkin.Span;

/**
 * A component that provides storage interfaces used for spans and aggregations. Implementations are
 * free to provide other interfaces, but the ones declared here must be supported.
 *
 * @see InMemoryStorage
 */
public interface StorageComponent extends Component {

  SpanStore spanStore();

  AsyncSpanStore asyncSpanStore();

  AsyncSpanConsumer asyncSpanConsumer();

  interface Builder {

    /**
     * Zipkin supports 64 and 128-bit trace identifiers, typically serialized as 16 or 32 character
     * hex strings. When false, this setting only considers the low 64-bits (right-most 16
     * characters) of a trace ID when grouping or retrieving traces. This should be set to false
     * while some applications issue 128-bit trace IDs and while other truncate them to 64-bit. If
     * 128-bit trace IDs are not in use, this setting is not required.
     *
     * <h3>Details</h3>
     *
     * <p>Zipkin historically had 64-bit {@link Span#traceId trace IDs}, but it now supports 128-bit
     * trace IDs via {@link Span#traceIdHigh}, or its 32-character hex representation. While
     * instrumentation update to propagate 128-bit IDs, it can be ambiguous whether a 64-bit trace
     * ID was sent intentionally, or as an accident of truncation. This setting allows Zipkin to be
     * usable until application instrumentation are upgraded to support 128-bit trace IDs.
     *
     * <p>Here are a few trace IDs the help explain this setting.
     *
     * <pre><ul>
     *   <li>Trace ID A: 463ac35c9f6413ad48485a3953bb6124</li>
     *   <li>Trace ID B: 48485a3953bb6124</li>
     *   <li>Trace ID C: 463ac35c9f6413adf1a48a8cff464e0e</li>
     *   <li>Trace ID D: 463ac35c9f6413ad</li>
     * </ul></pre>
     *
     * <p>In the above example, Trace ID A and Trace ID B might mean they are in the same trace,
     * since the lower-64 bits of the IDs are the same. This could happen if a server A created the
     * trace and propagated it to server B which ran an older tracing library. Server B could have
     * truncated the trace ID to lower-64 bits. When {@code strictTraceId == false},
     * spans matching either trace ID A or B would be returned in the same trace when searching by
     * ID A or B. Spans with trace ID C or D wouldn't be when searching by ID A or B because trace
     * IDs C and D don't share lower 64-bits (right-most 16 characters) with trace IDs A or B.
     *
     * <p>It is also possible that all servers are capable of handling 128-bit trace identifiers,
     * but are configured to only send 64-bit ones. In this case, if {@code
     * strictTraceId == false} trace ID A and B would clash and be put into the same
     * trace, causing confusion. Moreover, there is overhead associated with indexing spans both by
     * 64 and 128-bit trace IDs. When a site has finished upgrading to 128-bit trace IDs, they
     * should enable this setting.
     *
     * <p>See https://github.com/openzipkin/b3-propagation/issues/6 for the status of known open
     * source libraries on 128-bit trace identifiers.
     */
    Builder strictTraceId(boolean strictTraceId);

    StorageComponent build();
  }
}
