/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.server;

import okio.Buffer;
import okio.GzipSink;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import zipkin.Codec;
import zipkin.Span;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static zipkin.TestObjects.TRACE;
import static zipkin.internal.Util.UTF_8;

@SpringApplicationConfiguration(classes = ZipkinServer.class)
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@TestPropertySource(properties = {"zipkin.store.type=mem", "spring.config.name=zipkin-server"})
public class ZipkinServerIntegrationTests {

  @Autowired
  ConfigurableWebApplicationContext context;
  private MockMvc mockMvc;

  @Before
  public void init() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void writeSpans_noContentTypeIsJson() throws Exception {
    byte[] body = Codec.JSON.writeSpans(TRACE);
    mockMvc
        .perform(post("/api/v1/spans").content(body))
        .andExpect(status().isAccepted());
  }

  @Test
  public void writeSpans_updatesMetrics() throws Exception {
    byte[] body = Codec.JSON.writeSpans(TRACE);
    mockMvc.perform(post("/api/v1/spans").content(body));
    mockMvc.perform(post("/api/v1/spans").content(body));

    mockMvc
        .perform(get("/metrics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.['counter.zipkin_collector.messages.http']").value(2))
        .andExpect(jsonPath("$.['counter.zipkin_collector.bytes.http']").value(body.length * 2))
        .andExpect(jsonPath("$.['gauge.zipkin_collector.message_bytes.http']")
            .value(Double.valueOf(body.length))) // most recent size
        .andExpect(jsonPath("$.['counter.zipkin_collector.spans.http']").value(TRACE.size() * 2))
        .andExpect(jsonPath("$.['gauge.zipkin_collector.message_spans.http']")
            .value(Double.valueOf(TRACE.size()))); // most recent count
  }

  @Test
  public void writeSpans_malformedJsonIsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};
    mockMvc
        .perform(post("/api/v1/spans").content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(startsWith("Malformed reading List<Span> from json: hello")));
  }

  @Test
  public void writeSpans_malformedUpdatesMetrics() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};
    mockMvc.perform(post("/api/v1/spans").content(body));

    mockMvc
        .perform(get("/metrics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.['counter.zipkin_collector.messages.http']").value(1))
        .andExpect(jsonPath("$.['counter.zipkin_collector.messages_dropped.http']").value(1));
  }

  @Test
  public void writeSpans_malformedGzipIsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};
    mockMvc
        .perform(post("/api/v1/spans").content(body).header("Content-Encoding", "gzip"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(startsWith("Error gunzipping spans")));
  }

  @Test
  public void writeSpans_contentTypeXThrift() throws Exception {
    byte[] body = Codec.THRIFT.writeSpans(TRACE);
    mockMvc
        .perform(post("/api/v1/spans").content(body).contentType("application/x-thrift"))
        .andExpect(status().isAccepted());
  }

  @Test
  public void writeSpans_malformedThriftIsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};
    mockMvc
        .perform(post("/api/v1/spans").content(body).contentType("application/x-thrift"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(startsWith("Malformed reading List<Span> from TBinary: aGVsbG8=")));
  }

  @Test
  public void healthIsOK() throws Exception {
    mockMvc
        .perform(get("/health"))
        .andExpect(status().isOk());
  }

  public void writeSpans_gzipEncoded() throws Exception {
    byte[] body = Codec.JSON.writeSpans(TRACE);

    Buffer sink = new Buffer();
    GzipSink gzipSink = new GzipSink(sink);
    gzipSink.write(new Buffer().write(body), body.length);
    gzipSink.close();
    byte[] gzippedBody = sink.readByteArray();

    mockMvc
        .perform(post("/api/v1/spans").content(gzippedBody).header("Content-Encoding", "gzip"))
        .andExpect(status().isAccepted());
  }

  @Test
  public void readsRawTrace() throws Exception {
    Span span = TRACE.get(0);

    // write the span to the server, twice
    mockMvc.perform(post("/api/v1/spans").content(Codec.JSON.writeSpans(asList(span))))
        .andExpect(status().isAccepted());
    mockMvc.perform(post("/api/v1/spans").content(Codec.JSON.writeSpans(asList(span))))
        .andExpect(status().isAccepted());

    // sleep as the the storage operation is async
    Thread.sleep(1500);

    // Default will merge by span id
    mockMvc.perform(get("/api/v1/trace/1"))
        .andExpect(status().isOk())
        .andExpect(content().string(new String(Codec.JSON.writeSpans(asList(span)), UTF_8)));

    // In the in-memory (or cassandra) stores, a raw read will show duplicate span rows.
    mockMvc.perform(get("/api/v1/trace/1?raw"))
        .andExpect(status().isOk())
        .andExpect(content().string(new String(Codec.JSON.writeSpans(asList(span, span)), UTF_8)));
  }

  /** The zipkin-ui is a single-page app. This prevents reloading all resources on each click. */
  @Test
  public void setsMaxAgeOnUiResources() throws Exception {
    mockMvc.perform(get("/favicon.ico"))
        .andExpect(header().string("Cache-Control", "max-age=31536000"));
    mockMvc.perform(get("/config.json"))
        .andExpect(header().string("Cache-Control", "max-age=600"));
    mockMvc.perform(get("/index.html"))
        .andExpect(header().string("Cache-Control", "max-age=60"));
  }
}
