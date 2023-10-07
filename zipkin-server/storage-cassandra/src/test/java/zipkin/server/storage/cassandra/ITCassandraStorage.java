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

import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleConfigException;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleNotFoundException;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.junit.jupiter.MockitoExtension;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.storage.QueryRequest;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(120)
@ExtendWith(MockitoExtension.class)
public class ITCassandraStorage {
  @RegisterExtension
  CassandraExtension cassandra = new CassandraExtension();

  private final ModuleManager moduleManager = new ModuleManager();
  private SpanForward forward;
  private ITagAutoCompleteQueryDAO tagAutoCompleteQueryDAO;
  private IZipkinQueryDAO zipkinQueryDAO;
  @BeforeAll
  public void setup() throws ModuleNotFoundException, ModuleConfigException, ModuleStartException {
    final ApplicationConfigLoader loader = new ApplicationConfigLoader();
    moduleManager.init(loader.load());
    final ZipkinReceiverConfig config = new ZipkinReceiverConfig();
    config.setSearchableTracesTags("http.path");
    this.forward = new SpanForward(config, moduleManager);
    this.tagAutoCompleteQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ITagAutoCompleteQueryDAO.class);
    this.zipkinQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IZipkinQueryDAO.class);
  }

  @Test
  public void test() throws InterruptedException, IOException {
    final List<Span> traceSpans = TestObjects.newTrace("");
    forward.send(traceSpans);
    Thread.sleep(TimeUnit.SECONDS.toMillis(5));

    // service names
    final List<String> serviceNames = zipkinQueryDAO.getServiceNames();
    Assert.assertEquals(2, serviceNames.size());
    Assert.assertTrue(serviceNames.contains(TestObjects.FRONTEND.serviceName()));
    Assert.assertTrue(serviceNames.contains(TestObjects.BACKEND.serviceName()));

    // remote service names
    final List<String> remoteServiceNames = zipkinQueryDAO.getRemoteServiceNames(TestObjects.FRONTEND.serviceName());
    Assert.assertEquals(1, remoteServiceNames.size());
    Assert.assertEquals(TestObjects.BACKEND.serviceName(), remoteServiceNames.get(0));

    // span names
    final List<String> spanNames = zipkinQueryDAO.getSpanNames(TestObjects.BACKEND.serviceName());
    Assert.assertEquals(2, spanNames.size());
    Assert.assertTrue(spanNames.contains("query"));
    Assert.assertTrue(spanNames.contains("get"));

    // search traces
    final QueryRequest query = QueryRequest.newBuilder()
        .lookback(86400000L)
        .endTs(System.currentTimeMillis())
        .minDuration(1000L)
        .limit(10).build();
    Duration duration = new Duration();
    duration.setStep(Step.SECOND);
    DateTime endTime = new DateTime(query.endTs());
    DateTime startTime = endTime.minus(org.joda.time.Duration.millis(query.lookback()));
    duration.setStart(startTime.toString("yyyy-MM-dd HHmmss"));
    duration.setEnd(endTime.toString("yyyy-MM-dd HHmmss"));
    final List<List<Span>> traces = zipkinQueryDAO.getTraces(query, duration);
    Assert.assertEquals(1, traces.size());
    final List<Span> needsSpans = traceSpans.stream().filter(s -> s.duration() > 1000L).collect(Collectors.toList());
    Assert.assertEquals(needsSpans.size(), traces.get(0).size());
    for (Span needSpan : needsSpans) {
      Assert.assertTrue(traces.get(0).stream().anyMatch(needSpan::equals));
    }

    // get trace
    final List<Span> trace = zipkinQueryDAO.getTrace(traceSpans.get(0).traceId());
    Assert.assertEquals(traceSpans.size(), trace.size());
    for (Span span : traceSpans) {
      Assert.assertTrue(trace.stream().anyMatch(span::equals));
    }

    final Set<String> keys = tagAutoCompleteQueryDAO.queryTagAutocompleteKeys(TagType.ZIPKIN, 10, duration);
    Assert.assertEquals(1, keys.size());
    Assert.assertTrue(keys.contains("http.path"));

    final Set<String> values = tagAutoCompleteQueryDAO.queryTagAutocompleteValues(TagType.ZIPKIN, "http.path", 10, duration);
    Assert.assertEquals(1, values.size());
    Assert.assertTrue(values.contains("/api"));
  }

}
