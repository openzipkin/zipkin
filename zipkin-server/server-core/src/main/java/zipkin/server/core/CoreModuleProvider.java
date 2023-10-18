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
package zipkin.server.core;

import com.linecorp.armeria.common.HttpMethod;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.cache.ProfileTaskCache;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.management.ui.menu.UIMenuManagementService;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateManagementService;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.profiling.continuous.ContinuousProfilingMutationService;
import org.apache.skywalking.oap.server.core.profiling.continuous.ContinuousProfilingQueryService;
import org.apache.skywalking.oap.server.core.profiling.ebpf.EBPFProfilingMutationService;
import org.apache.skywalking.oap.server.core.profiling.ebpf.EBPFProfilingQueryService;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskMutationService;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskQueryService;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.AlarmQueryService;
import org.apache.skywalking.oap.server.core.query.BrowserLogQueryService;
import org.apache.skywalking.oap.server.core.query.EventQueryService;
import org.apache.skywalking.oap.server.core.query.LogQueryService;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsMetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsQueryService;
import org.apache.skywalking.oap.server.core.query.RecordQueryService;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.TopNRecordsQueryService;
import org.apache.skywalking.oap.server.core.query.TopologyQueryService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.RemoteServiceHandler;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.remote.health.HealthCheckServiceHandler;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.status.ServerStatusService;
import org.apache.skywalking.oap.server.core.storage.PersistenceTimer;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.ModelManipulator;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.ttl.DataTTLKeeperTimer;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.core.worker.WorkerInstancesService;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.telemetry.api.TelemetryRelatedContext;
import zipkin.server.core.services.EmptyComponentLibraryCatalogService;
import zipkin.server.core.services.EmptyNetworkAddressAliasCache;
import zipkin.server.core.services.HTTPConfigurableServer;
import zipkin.server.core.services.HTTPInfoHandler;
import zipkin.server.core.services.ZipkinConfigService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class CoreModuleProvider extends ModuleProvider {
  private CoreModuleConfig moduleConfig;

  private EndpointNameGrouping endpointNameGrouping;
  private final ZipkinSourceReceiverImpl receiver;
  private final ZipkinAnnotationScan annotationScan;
  private final StorageModels storageModels;
  private RemoteClientManager remoteClientManager;
  private GRPCServer grpcServer;
  private HTTPServer httpServer;

  public CoreModuleProvider() {
    this.annotationScan = new ZipkinAnnotationScan();
    this.receiver = new ZipkinSourceReceiverImpl();
    this.storageModels = new StorageModels();
  }

  @Override
  public String name() {
    return "zipkin";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return CoreModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<CoreModuleConfig>() {
      @Override
      public Class<CoreModuleConfig> type() {
        return CoreModuleConfig.class;
      }

      @Override
      public void onInitialized(CoreModuleConfig initialized) {
        moduleConfig = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    endpointNameGrouping = new EndpointNameGrouping();
    final NamingControl namingControl = new NamingControl(
        moduleConfig.getServiceNameMaxLength(),
        moduleConfig.getInstanceNameMaxLength(),
        moduleConfig.getEndpointNameMaxLength(),
        endpointNameGrouping
    );
    this.registerServiceImplementation(NamingControl.class, namingControl);

    annotationScan.registerListener(new DefaultScopeDefine.Listener());
    annotationScan.registerListener(new ZipkinStreamAnnotationListener(getManager()));

    HTTPServerConfig httpServerConfig = HTTPServerConfig.builder()
        .host(moduleConfig.getRestHost())
        .port(moduleConfig.getRestPort())
        .contextPath(moduleConfig.getRestContextPath())
        .idleTimeOut(moduleConfig.getRestIdleTimeOut())
        .maxThreads(moduleConfig.getRestMaxThreads())
        .acceptQueueSize(
            moduleConfig.getRestAcceptQueueSize())
        .maxRequestHeaderSize(
            moduleConfig.getRestMaxRequestHeaderSize())
        .build();
    httpServer = new HTTPConfigurableServer(httpServerConfig);
    httpServer.initialize();
    // "/info" handler
    httpServer.addHandler(new HTTPInfoHandler(), Arrays.asList(HttpMethod.GET, HttpMethod.POST));

    // grpc
    if (moduleConfig.getGRPCSslEnabled()) {
      grpcServer = new GRPCServer(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort(),
          moduleConfig.getGRPCSslCertChainPath(),
          moduleConfig.getGRPCSslKeyPath(),
          null
      );
    } else {
      grpcServer = new GRPCServer(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort());
    }
    if (moduleConfig.getGRPCMaxConcurrentCallsPerConnection() > 0) {
      grpcServer.setMaxConcurrentCallsPerConnection(moduleConfig.getGRPCMaxConcurrentCallsPerConnection());
    }
    if (moduleConfig.getGRPCMaxMessageSize() > 0) {
      grpcServer.setMaxMessageSize(moduleConfig.getGRPCMaxMessageSize());
    }
    if (moduleConfig.getGRPCThreadPoolQueueSize() > 0) {
      grpcServer.setThreadPoolQueueSize(moduleConfig.getGRPCThreadPoolQueueSize());
    }
    if (moduleConfig.getGRPCThreadPoolSize() > 0) {
      grpcServer.setThreadPoolSize(moduleConfig.getGRPCThreadPoolSize());
    }
    grpcServer.initialize();

    if (moduleConfig.getGRPCSslEnabled()) {
      this.remoteClientManager = new RemoteClientManager(getManager(), moduleConfig.getRemoteTimeout(),
          moduleConfig.getGRPCSslTrustedCAPath()
      );
    } else {
      this.remoteClientManager = new RemoteClientManager(getManager(), moduleConfig.getRemoteTimeout());
    }

    final org.apache.skywalking.oap.server.core.CoreModuleConfig swConfig = this.moduleConfig.toSkyWalkingConfig();
    this.registerServiceImplementation(MeterSystem.class, new MeterSystem(getManager()));
    this.registerServiceImplementation(ConfigService.class, new ZipkinConfigService(moduleConfig, this));
    this.registerServiceImplementation(ServerStatusService.class, new ServerStatusService(getManager()));
    this.registerServiceImplementation(DownSamplingConfigService.class, new DownSamplingConfigService(Collections.emptyList()));
    this.registerServiceImplementation(GRPCHandlerRegister.class, new GRPCHandlerRegisterImpl(grpcServer));
    this.registerServiceImplementation(HTTPHandlerRegister.class, new HTTPHandlerRegisterImpl(httpServer));
    this.registerServiceImplementation(IComponentLibraryCatalogService.class, new EmptyComponentLibraryCatalogService());
    this.registerServiceImplementation(SourceReceiver.class, receiver);
    final WorkerInstancesService instancesService = new WorkerInstancesService();
    this.registerServiceImplementation(IWorkerInstanceGetter.class, instancesService);
    this.registerServiceImplementation(IWorkerInstanceSetter.class, instancesService);
    this.registerServiceImplementation(RemoteSenderService.class, new RemoteSenderService(getManager()));
    this.registerServiceImplementation(ModelCreator.class, storageModels);
    this.registerServiceImplementation(IModelManager.class, storageModels);
    this.registerServiceImplementation(ModelManipulator.class, storageModels);
    this.registerServiceImplementation(NetworkAddressAliasCache.class, new EmptyNetworkAddressAliasCache());
    this.registerServiceImplementation(TopologyQueryService.class, new TopologyQueryService(getManager(), storageModels));
    this.registerServiceImplementation(MetricsMetadataQueryService.class, new MetricsMetadataQueryService());
    this.registerServiceImplementation(MetricsQueryService.class, new MetricsQueryService(getManager()));
    this.registerServiceImplementation(TraceQueryService.class, new TraceQueryService(getManager()));
    this.registerServiceImplementation(BrowserLogQueryService.class, new BrowserLogQueryService(getManager()));
    this.registerServiceImplementation(LogQueryService.class, new LogQueryService(getManager()));
    this.registerServiceImplementation(MetadataQueryService.class, new MetadataQueryService(getManager(), swConfig));
    this.registerServiceImplementation(AggregationQueryService.class, new AggregationQueryService(getManager()));
    this.registerServiceImplementation(AlarmQueryService.class, new AlarmQueryService(getManager()));
    this.registerServiceImplementation(TopNRecordsQueryService.class, new TopNRecordsQueryService(getManager()));
    this.registerServiceImplementation(EventQueryService.class, new EventQueryService(getManager()));
    this.registerServiceImplementation(TagAutoCompleteQueryService.class, new TagAutoCompleteQueryService(getManager(), swConfig));
    this.registerServiceImplementation(RecordQueryService.class, new RecordQueryService(getManager()));
    this.registerServiceImplementation(ProfileTaskMutationService.class, new ProfileTaskMutationService(getManager()));
    this.registerServiceImplementation(ProfileTaskQueryService.class, new ProfileTaskQueryService(getManager(), swConfig));
    this.registerServiceImplementation(ProfileTaskCache.class, new ProfileTaskCache(getManager(), swConfig));
    this.registerServiceImplementation(EBPFProfilingMutationService.class, new EBPFProfilingMutationService(getManager()));
    this.registerServiceImplementation(EBPFProfilingQueryService.class, new EBPFProfilingQueryService(getManager(), swConfig, this.storageModels));
    this.registerServiceImplementation(ContinuousProfilingMutationService.class, new ContinuousProfilingMutationService(getManager()));
    this.registerServiceImplementation(ContinuousProfilingQueryService.class, new ContinuousProfilingQueryService(getManager()));
    this.registerServiceImplementation(CommandService.class, new CommandService(getManager()));
    this.registerServiceImplementation(OALEngineLoaderService.class, new OALEngineLoaderService(getManager()));
    this.registerServiceImplementation(RemoteClientManager.class, remoteClientManager);
    this.registerServiceImplementation(UITemplateManagementService.class, new UITemplateManagementService(getManager()));
    this.registerServiceImplementation(UIMenuManagementService.class, new UIMenuManagementService(getManager(), swConfig));

    if (moduleConfig.getMetricsDataTTL() < 2) {
      throw new ModuleStartException(
          "Metric TTL should be at least 2 days, current value is " + moduleConfig.getMetricsDataTTL());
    }
    if (moduleConfig.getRecordDataTTL() < 2) {
      throw new ModuleStartException(
          "Record TTL should be at least 2 days, current value is " + moduleConfig.getRecordDataTTL());
    }

    final MetricsStreamProcessor metricsStreamProcessor = MetricsStreamProcessor.getInstance();
    metricsStreamProcessor.setL1FlushPeriod(moduleConfig.getL1FlushPeriod());
    metricsStreamProcessor.setStorageSessionTimeout(moduleConfig.getStorageSessionTimeout());
    metricsStreamProcessor.setMetricsDataTTL(moduleConfig.getMetricsDataTTL());
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    grpcServer.addHandler(new RemoteServiceHandler(getManager()));
    grpcServer.addHandler(new HealthCheckServiceHandler());

    try {
      receiver.scan();
      annotationScan.scan();
    } catch (IOException | IllegalAccessException | InstantiationException | StorageException e) {
      throw new ModuleStartException(e.getMessage(), e);
    }

    Address gRPCServerInstanceAddress = new Address(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort(), true);
    TelemetryRelatedContext.INSTANCE.setId(gRPCServerInstanceAddress.toString());
    ClusterCoordinator coordinator = this.getManager()
        .find(ClusterModule.NAME)
        .provider()
        .getService(ClusterCoordinator.class);
    coordinator.registerWatcher(remoteClientManager);
    coordinator.start();
    RemoteInstance gRPCServerInstance = new RemoteInstance(gRPCServerInstanceAddress);
    coordinator.registerRemote(gRPCServerInstance);
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    try {
      if (!RunningMode.isInitMode()) {
        grpcServer.start();
        httpServer.start();
        remoteClientManager.start();
      }
    } catch (ServerException e) {
      throw new ModuleStartException(e.getMessage(), e);
    }

    final org.apache.skywalking.oap.server.core.CoreModuleConfig swConfig = this.moduleConfig.toSkyWalkingConfig();
    PersistenceTimer.INSTANCE.start(getManager(), swConfig);
    DataTTLKeeperTimer.INSTANCE.start(getManager(), swConfig);
  }

  @Override
  public String[] requiredModules() {
    return new String[0];
  }
}
