/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin.server.core.services;

import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import zipkin.server.core.CoreModuleConfig;

public class ZipkinConfigService extends ConfigService {
  private CoreModuleConfig moduleConfig;

  public ZipkinConfigService(CoreModuleConfig moduleConfig, ModuleProvider provider) {
    super(new org.apache.skywalking.oap.server.core.CoreModuleConfig(), provider);
    this.moduleConfig = moduleConfig;
  }

  public ZipkinReceiverConfig toZipkinReceiverConfig() {
    final ZipkinReceiverConfig config = new ZipkinReceiverConfig();
    config.setSearchableTracesTags(moduleConfig.getSearchableTracesTags());
    config.setSampleRate((int) (moduleConfig.getTraceSampleRate() * 10000));
    return config;
  }
}
