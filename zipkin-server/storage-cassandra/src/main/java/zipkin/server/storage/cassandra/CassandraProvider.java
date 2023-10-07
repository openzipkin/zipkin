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

package zipkin.server.storage.cassandra;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IServiceLabelDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import zipkin.server.storage.cassandra.dao.CassandraBatchDAO;
import zipkin.server.storage.cassandra.dao.CassandraHistoryDeleteDAO;
import zipkin.server.storage.cassandra.dao.CassandraStorageDAO;
import zipkin.server.storage.cassandra.dao.CassandraTagAutocompleteDAO;
import zipkin.server.storage.cassandra.dao.CassandraZipkinQueryDAO;
import zipkin.server.storage.cassandra.dao.EmptyDAO;

import java.time.Clock;

public class CassandraProvider extends ModuleProvider {
  private CassandraConfig moduleConfig;
  private CassandraClient client;
  private CassandraTableInstaller modelInstaller;
  private CassandraTableHelper tableHelper;

  @Override
  public String name() {
    return "cassandra";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return StorageModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<CassandraConfig>() {
      @Override
      public Class<CassandraConfig> type() {
        return CassandraConfig.class;
      }

      @Override
      public void onInitialized(CassandraConfig initialized) {
        moduleConfig = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    client = new CassandraClient(moduleConfig);
    modelInstaller = new CassandraTableInstaller(client, getManager());
    tableHelper = new CassandraTableHelper(getManager(), client);

    this.registerServiceImplementation(
        StorageBuilderFactory.class,
        new StorageBuilderFactory.Default());
    this.registerServiceImplementation(
        IBatchDAO.class,
        new CassandraBatchDAO(client, moduleConfig.getMaxSizeOfBatchCql(), moduleConfig.getAsyncBatchPersistentPoolSize())
    );
    this.registerServiceImplementation(
        StorageDAO.class,
        new CassandraStorageDAO(client)
    );

    final EmptyDAO emptyDAO = new EmptyDAO();
    this.registerServiceImplementation(INetworkAddressAliasDAO.class, emptyDAO);
    this.registerServiceImplementation(ITopologyQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(ITraceQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IMetricsQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(ILogQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IMetadataQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IAggregationQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IAlarmQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IRecordsQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IBrowserLogQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IProfileTaskQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IProfileThreadSnapshotQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(UITemplateManagementDAO.class, emptyDAO);
    this.registerServiceImplementation(UIMenuManagementDAO.class, emptyDAO);
    this.registerServiceImplementation(IEventQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(IEBPFProfilingTaskDAO.class, emptyDAO);
    this.registerServiceImplementation(IEBPFProfilingScheduleDAO.class, emptyDAO);
    this.registerServiceImplementation(IEBPFProfilingDataDAO.class, emptyDAO);
    this.registerServiceImplementation(IContinuousProfilingPolicyDAO.class, emptyDAO);
    this.registerServiceImplementation(IServiceLabelDAO.class, emptyDAO);
    this.registerServiceImplementation(ITagAutoCompleteQueryDAO.class, emptyDAO);
    this.registerServiceImplementation(ISpanAttachedEventQueryDAO.class, emptyDAO);

    this.registerServiceImplementation(IHistoryDeleteDAO.class, new CassandraHistoryDeleteDAO(client, tableHelper, modelInstaller, Clock.systemDefaultZone()));
    this.registerServiceImplementation(IZipkinQueryDAO.class, new CassandraZipkinQueryDAO(client, tableHelper));
    this.registerServiceImplementation(ITagAutoCompleteQueryDAO.class, new CassandraTagAutocompleteDAO(client, tableHelper));

  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    MetricsCreator metricCreator =
        getManager()
            .find(TelemetryModule.NAME)
            .provider()
            .getService(MetricsCreator.class);
    HealthCheckMetrics healthChecker =
        metricCreator.createHealthCheckerGauge(
            "storage_" + name(),
            MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE);
    client.registerChecker(healthChecker);
    try {
      client.connect();
      modelInstaller.start();

      getManager()
          .find(CoreModule.NAME)
          .provider()
          .getService(ModelCreator.class)
          .addModelListener(modelInstaller);
    } catch (Exception e) {
      throw new ModuleStartException(e.getMessage(), e);
    }
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

  }

  @Override
  public String[] requiredModules() {
    return new String[] {CoreModule.NAME};
  }
}
