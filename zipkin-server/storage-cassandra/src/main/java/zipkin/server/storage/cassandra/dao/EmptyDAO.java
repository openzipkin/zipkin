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

package zipkin.server.storage.cassandra.dao;

import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventTraceType;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.management.ui.menu.UIMenu;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.NullableValue;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
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
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class EmptyDAO implements
    INetworkAddressAliasDAO,
    ITopologyQueryDAO,
    IMetricsDAO,
    ILogQueryDAO,
    ITraceQueryDAO,
    IMetricsQueryDAO,
    IAggregationQueryDAO,
    IAlarmQueryDAO,
    IRecordsQueryDAO,
    IRecordDAO,
    IBrowserLogQueryDAO,
    IMetadataQueryDAO,
    IProfileTaskQueryDAO,
    IProfileTaskLogQueryDAO,
    IProfileThreadSnapshotQueryDAO,
    UITemplateManagementDAO,
    UIMenuManagementDAO,
    IEventQueryDAO,
    IEBPFProfilingTaskDAO,
    IEBPFProfilingScheduleDAO,
    IEBPFProfilingDataDAO,
    IContinuousProfilingPolicyDAO,
    IServiceLabelDAO
{
  @Override
  public List<NetworkAddressAlias> loadLastUpdate(long timeBucket) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UIMenu getMenu(String id) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public void saveMenu(UIMenu menu) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DashboardConfiguration getTemplate(String id) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public TemplateChangeStatus addTemplate(DashboardSetting setting) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public TemplateChangeStatus changeTemplate(DashboardSetting setting) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public TemplateChangeStatus disableTemplate(String id) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public void savePolicy(ContinuousProfilingPolicy policy) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<ContinuousProfilingPolicy> queryPolicies(List<String> serviceIdList) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<EBPFProfilingDataRecord> queryData(List<String> scheduleIdList, long beginTime, long endTime) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<EBPFProfilingSchedule> querySchedules(String taskId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<EBPFProfilingTaskRecord> queryTasksByServices(List<String> serviceIdList, EBPFProfilingTriggerType triggerType, long taskStartTime, long latestUpdateTime) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<EBPFProfilingTaskRecord> queryTasksByTargets(String serviceId, String serviceInstanceId, List<EBPFProfilingTargetType> targetTypes, EBPFProfilingTriggerType triggerType, long taskStartTime, long latestUpdateTime) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<EBPFProfilingTaskRecord> getTaskRecord(String id) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<String> queryAllLabels(String serviceId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<ProfileTaskLog> getTaskLogList() throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<String> queryProfiledSegmentIdList(String taskId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public int queryMinSequence(String segmentId, long start, long end) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<SelectedRecord> sortMetrics(TopNCondition condition, String valueColumnName, Duration duration, List<KeyValue> additionalConditions) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, Duration duration, List<Tag> tags) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public void parserDataBinaryBase64(String dataBinaryBase64, List<KeyValue> tags) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public BrowserErrorLogs queryBrowserErrorLogs(String serviceId, String serviceVersionId, String pagePathId, BrowserErrorCategory category, Duration duration, int limit, int from) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public BrowserErrorLog parserDataBinary(String dataBinaryBase64) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public BrowserErrorLog parserDataBinary(byte[] dataBinary) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public Events queryEvents(EventQueryCondition condition) throws Exception {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration, List<String> serviceIds) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration, List<String> serviceIds) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId, String serverServiceId, Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId, String serverServiceId, Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Call.CallDetail> loadEndpointRelation(Duration duration, String destEndpointId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Call.CallDetail> loadProcessRelationDetectedAtClientSide(String serviceInstanceId, Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Call.CallDetail> loadProcessRelationDetectedAtServerSide(String serviceInstanceId, Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public TraceBrief queryBasicTraces(Duration duration, long minDuration, long maxDuration, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<SegmentRecord> queryBySegmentIdList(List<String> segmentIdList) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<SegmentRecord> queryByTraceIdWithInstanceId(List<String> traceIdList, List<String> instanceIdList) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<ProfileTask> getTaskList(String serviceId, String endpointName, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProfileTask getById(String id) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws Exception {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public InsertRequest prepareBatchInsert(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Service> listServices() throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<ServiceInstance> listInstances(Duration duration, String serviceId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ServiceInstance getInstance(String instanceId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<ServiceInstance> getInstances(List<String> instanceIds) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Endpoint> findEndpoint(String keyword, String serviceId, int limit) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Process> listProcesses(String serviceId, ProfilingSupportStatus supportStatus, long lastPingStartTimeBucket, long lastPingEndTimeBucket) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Process> listProcesses(String serviceInstanceId, Duration duration, boolean includeVirtual) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<Process> listProcesses(String agentId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public long getProcessCount(String serviceId, ProfilingSupportStatus profilingSupportStatus, long lastPingStartTimeBucket, long lastPingEndTimeBucket) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public long getProcessCount(String instanceId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public Process getProcess(String processId) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<org.apache.skywalking.oap.server.core.query.type.Record> readRecords(RecordCondition condition, String valueColumnName, Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public NullableValue readMetricsValue(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public MetricsValues readMetricsValues(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition, String valueColumnName, List<String> labels, Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public HeatMap readHeatMap(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public boolean supportQueryLogsByKeywords() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public Logs queryLogs(String serviceId, String serviceInstanceId, String endpointId, TraceScopeCondition relatedTrace, Order queryOrder, int from, int limit, Duration duration, List<Tag> tags, List<String> keywordsOfContent, List<String> excludingKeywordsOfContent) throws IOException {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public void parserDataBinary(String dataBinaryBase64, List<KeyValue> tags) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public void parserDataBinary(byte[] dataBinary, List<KeyValue> tags) {
    throw new IllegalStateException("Not implemented");
  }
}
