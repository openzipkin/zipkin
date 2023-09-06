/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package zipkin.server;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.status.ServerStatusService;
import org.apache.skywalking.oap.server.core.version.Version;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.server.config.ApplicationConfigLoader;

/**
 * Starter core. Load the core configuration file, and initialize the startup sequence through {@link ModuleManager}.
 */
public class ZipkinServerBootstrap {
    private static final Logger log = LoggerFactory.getLogger(ZipkinServerBootstrap.class);

    public static void start() {
        String mode = System.getProperty("mode");
        RunningMode.setMode(mode);

        ApplicationConfigLoader configLoader = new ApplicationConfigLoader();
        ModuleManager manager = new ModuleManager();
        try {
            ApplicationConfiguration applicationConfiguration = configLoader.load();
            manager.init(applicationConfiguration);

            manager.find(CoreModule.NAME)
                   .provider()
                   .getService(ServerStatusService.class)
                   .bootedNow(System.currentTimeMillis());

            log.info("Version of Zipkin: {}", Version.CURRENT);

            if (RunningMode.isInitMode()) {
                log.info("Zipkin starts up in init mode successfully, exit now...");
                System.exit(0);
            }
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            System.exit(1);
        }
    }
}
