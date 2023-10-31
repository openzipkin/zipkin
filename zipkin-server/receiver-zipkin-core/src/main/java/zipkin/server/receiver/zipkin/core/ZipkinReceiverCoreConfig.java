/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.receiver.zipkin.core;

import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class ZipkinReceiverCoreConfig extends ModuleConfig {

  /**
   * The trace sample rate precision is 0.0001, should be between 0 and 1.
   */
  private double traceSampleRate = 1.0f;

  /**
   * Defines a set of span tag keys which are searchable.
   * The max length of key=value should be less than 256 or will be dropped.
   */
  private String searchableTracesTags = DEFAULT_SEARCHABLE_TAG_KEYS;

  private static final String DEFAULT_SEARCHABLE_TAG_KEYS = String.join(
      Const.COMMA,
      "http.method"
  );

  public double getTraceSampleRate() {
    return traceSampleRate;
  }

  public void setTraceSampleRate(double traceSampleRate) {
    this.traceSampleRate = traceSampleRate;
  }

  public String getSearchableTracesTags() {
    return searchableTracesTags;
  }

  public void setSearchableTracesTags(String searchableTracesTags) {
    this.searchableTracesTags = searchableTracesTags;
  }
}
