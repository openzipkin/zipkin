/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.internal;

import io.zipkin.Span;

/**
 * <h3>Derived timestamp and duration</h3>
 *
 * <p/>Instrumentation should log timestamp and duration, but since these fields are recent
 * (Nov-2015), a lot of tracers will not. Accordingly, this will backfill timestamp and duration to
 * if possible, based on interpretation of annotations.
 */
public class ApplyTimestampAndDuration {

  public static Span apply(Span s) {
    if ((s.timestamp == null || s.duration == null) && !s.annotations.isEmpty()) {
      Long ts = s.timestamp;
      Long dur = s.duration;
      ts = ts != null ? ts : s.annotations.get(0).timestamp;
      if (dur == null) {
        long lastTs = s.annotations.get(s.annotations.size() - 1).timestamp;
        if (ts != lastTs) {
          dur = lastTs - ts;
        }
      }
      return new Span.Builder(s).timestamp(ts).duration(dur).build();
    }
    return s;
  }

  private ApplyTimestampAndDuration() {
  }
}
