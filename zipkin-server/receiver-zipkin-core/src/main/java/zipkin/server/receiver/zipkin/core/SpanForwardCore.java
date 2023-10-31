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
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SpanForwardCore extends SpanForward {
  private final ZipkinReceiverConfig config;
  public SpanForwardCore(ZipkinReceiverConfig config, ModuleManager manager) {
    super(config, manager);
    this.config = config;
  }

  public Set<String> getTagAutocompleteKeys() {
    return new HashSet<>(Arrays.asList(config.getSearchableTracesTags().split(Const.COMMA)));
  }
}
