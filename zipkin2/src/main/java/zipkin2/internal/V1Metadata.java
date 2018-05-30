/*
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
package zipkin2.internal;

import zipkin2.Span;

public final class V1Metadata {
  public final long startTs, endTs;
  public final String begin, end, remoteEndpointType;

  V1Metadata(long startTs, long endTs, String begin, String end, String remoteEndpointType) {
    this.startTs = startTs;
    this.endTs = endTs;
    this.begin = begin;
    this.end = end;
    this.remoteEndpointType = remoteEndpointType;
  }

  public static V1Metadata parse(Span in) {
    long startTs, endTs;
    String begin = null, end = null;
    String remoteEndpointType = null;

    startTs = in.timestampAsLong();
    endTs = startTs != 0L && in.durationAsLong() != 0L ? startTs + in.durationAsLong() : 0L;

    if (in.kind() != null) {
      switch (in.kind()) {
        case CLIENT:
          remoteEndpointType = "sa";
          begin = "cs";
          end = "cr";
          break;
        case SERVER:
          remoteEndpointType = "ca";
          begin = "sr";
          end = "ss";
          break;
        case PRODUCER:
          remoteEndpointType = "ma";
          begin = "ms";
          end = "ws";
          break;
        case CONSUMER:
          remoteEndpointType = "ma";
          if (endTs != 0L) {
            begin = "wr";
            end = "mr";
          } else {
            begin = "mr";
          }
          break;
        default:
          throw new AssertionError("update kind mapping");
      }
    }
    return new V1Metadata(startTs, endTs, begin, end, remoteEndpointType);
  }
}
