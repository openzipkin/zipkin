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
package zipkin.internal;

import zipkin.Annotation;
import zipkin.Constants;
import zipkin.Span;

/**
 * <h3>Derived timestamp and duration</h3>
 *
 * <p>Instrumentation should log timestamp and duration in most cases, but since these fields are
 * recent (Nov-2015), a lot of tracers will not. They also will not log timestamp or duration in
 * one-way spans ("cs", "sr"). This includes a utility to backfill timestamp and duration at query
 * time. It also includes a utility to guess a timestamp, which is useful when indexing incomplete
 * spans.
 */
public class ApplyTimestampAndDuration {

  /**
   * For RPC two-way spans, the duration between "cs" and "cr" is authoritative. RPC one-way spans
   * lack a response, so the duration is between "cs" and "sr". We special-case this to avoid
   * setting incorrect duration when there's skew between the client and the server.
   *
   * <p>Note: this should only be used for query, not storage commands!
   */
  public static Span apply(Span span) {
    // Don't overwrite authoritatively set timestamp and duration!
    if (span.timestamp != null && span.duration != null) {
      return span;
    }

    // We cannot backfill duration on a span with less than two annotations. However, we can
    // backfill timestamp.
    if (span.annotations.size() < 2) {
      if (span.timestamp != null) return span;
      Long guess = guessTimestamp(span);
      if (guess == null) return span;
      return span.toBuilder().timestamp(guess).build();
    }

    // Prefer RPC one-way (cs -> sr) vs arbitrary annotations.
    Long first = span.annotations.get(0).timestamp;
    Long last = span.annotations.get(span.annotations.size() - 1).timestamp;
    for (int i = 0, length = span.annotations.size(); i < length; i++) {
      Annotation annotation = span.annotations.get(i);
      if (annotation.value.equals(Constants.CLIENT_SEND)) {
        first = annotation.timestamp;
      } else if (annotation.value.equals(Constants.CLIENT_RECV)) {
        last = annotation.timestamp;
      }
    }
    long ts = span.timestamp != null ? span.timestamp : first;
    Long dur = span.duration != null ? span.duration : last.equals(first) ? null : last - first;
    return span.toBuilder().timestamp(ts).duration(dur).build();
  }

  /**
   * Instrumentation should set {@link Span#timestamp} when recording a span so that guess-work
   * isn't needed. Since a lot of instrumentation don't, we have to make some guesses.
   *
   * <pre><ul>
   *   <li>If there is a {@link Constants#CLIENT_SEND}, use that</li>
   *   <li>Fall back to {@link Constants#SERVER_RECV}</li>
   *   <li>Otherwise, return null</li>
   * </ul></pre>
   */
  public static Long guessTimestamp(Span span) {
    if (span.timestamp != null || span.annotations.isEmpty()) return span.timestamp;
    Long rootServerRecv = null;
    for (int i = 0, length = span.annotations.size(); i < length; i++) {
      Annotation annotation = span.annotations.get(i);
      if (annotation.value.equals(Constants.CLIENT_SEND)) {
        return annotation.timestamp;
      } else if (annotation.value.equals(Constants.SERVER_RECV)) {
        rootServerRecv = annotation.timestamp;
      }
    }
    return rootServerRecv;
  }

  /** When performing updates, don't overwrite an authoritative timestamp with a guess! */
  public static Long authoritativeTimestamp(Span span) {
    if (span.timestamp != null) return span.timestamp;
    for (int i = 0, length = span.annotations.size(); i < length; i++) {
      Annotation a = span.annotations.get(i);
      if (a.value.equals(Constants.CLIENT_SEND)) {
        return a.timestamp;
      }
    }
    return null;
  }

  private ApplyTimestampAndDuration() {
  }
}
