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

package zipkin.server.core.services;

import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.core.remote.client.SelfRemoteClient;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.selector.Selector;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class SelfSenderService extends RemoteSenderService {
  private final ModuleManager moduleManager;
  private SelfRemoteClient self;

  public SelfSenderService(ModuleManager moduleManager) {
    super(moduleManager);
    this.moduleManager = moduleManager;
  }

  private SelfRemoteClient getSelf() {
    if (self == null) {
      self = new SelfRemoteClient(moduleManager, new Address("127.0.0.1", 0, true));
    }
    return self;
  }

  @Override
  public void send(String nextWorkName, StreamData streamData, Selector selector) {
    getSelf().push(nextWorkName, streamData);
  }
}
