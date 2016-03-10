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
package zipkin.internal;

import java.util.List;
import zipkin.Annotation;
import zipkin.Constants;
import zipkin.Span;

/**
 * <h3>Derived timestamp and duration</h3>
 *
 * <p/>Instrumentation should log timestamp and duration, but since these fields are recent
 * (Nov-2015), a lot of tracers will not. Accordingly, this will backfill timestamp and duration to
 * if possible, based on interpretation of annotations.
 */
public class ApplyTimestampAndDuration {

  // For spans that core client annotations, the distance between "cs" and "cr" should be the
  // authoritative duration. We are special-casing this to avoid setting incorrect duration
  // when there's skew between the client and the server.
  public static Span apply(Span s) {
    if ((s.timestamp == null || s.duration == null) && !s.annotations.isEmpty()) {
      Long ts = s.timestamp;
      Long dur = s.duration;
      ts = ts != null ? ts : getFirstTimestamp(s.annotations);
      if (dur == null) {
        long lastTs = getLastTimestamp(s.annotations);
        if (ts != lastTs) {
          dur = lastTs - ts;
        }
      }
      return new Span.Builder(s).timestamp(ts).duration(dur).build();
    }
    return s;
  }

  static long getFirstTimestamp(List<Annotation> annotations) {
    for (int i = 0, length = annotations.size(); i < length; i++) {
      if (annotations.get(i).value.equals(Constants.CLIENT_SEND)) {
        return annotations.get(i).timestamp;
      }
    }
    return annotations.get(0).timestamp;
  }

  static long getLastTimestamp(List<Annotation> annotations) {
    int length = annotations.size();
    for (int i = 0; i < length; i++) {
      if (annotations.get(i).value.equals(Constants.CLIENT_RECV)) {
        return annotations.get(i).timestamp;
      }
    }
    return annotations.get(length - 1).timestamp;
  }

  private ApplyTimestampAndDuration() {
  }
}
