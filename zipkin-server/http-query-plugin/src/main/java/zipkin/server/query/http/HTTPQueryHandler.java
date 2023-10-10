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

package zipkin.server.query.http;

import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import org.apache.skywalking.oap.query.zipkin.handler.ZipkinQueryHandler;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.linecorp.armeria.common.HttpStatus.BAD_REQUEST;
import static com.linecorp.armeria.common.HttpStatus.NOT_FOUND;
import static com.linecorp.armeria.common.MediaType.ANY_TEXT_TYPE;

public class HTTPQueryHandler extends ZipkinQueryHandler {
  private final HTTPQueryConfig config;
  private final ModuleManager moduleManager;

  private IZipkinQueryDAO zipkinQueryDAO;
  public HTTPQueryHandler(HTTPQueryConfig config, ModuleManager moduleManager) {
    super(config.toSkyWalkingConfig(), moduleManager);
    this.config = config;
    this.moduleManager = moduleManager;
  }

  @Override
  public AggregatedHttpResponse getTraceById(String traceId) throws IOException {
    if (StringUtil.isEmpty(traceId)) {
      return AggregatedHttpResponse.of(BAD_REQUEST, ANY_TEXT_TYPE, "traceId is empty or null");
    }
    final String normalized = Span.normalizeTraceId(traceId.trim());
    List<Span> result;
    if (!config.getStrictTraceId() && normalized.length() == 32) {
      result = getZipkinQueryDAO().getTraces(new HashSet<>(Arrays.asList(normalized, normalized.substring(16))))
          .stream().flatMap(List::stream).collect(Collectors.toList());
    } else {
      result = getZipkinQueryDAO().getTrace(normalized);
    }
    if (CollectionUtils.isEmpty(result)) {
      return AggregatedHttpResponse.of(NOT_FOUND, ANY_TEXT_TYPE, traceId + " not found");
    }
    return response(SpanBytesEncoder.JSON_V2.encodeList(result));
  }

  @Override
  public AggregatedHttpResponse getTracesByIds(String traceIds) throws IOException {
    if (StringUtil.isEmpty(traceIds)) {
      return AggregatedHttpResponse.of(BAD_REQUEST, ANY_TEXT_TYPE, "traceIds is empty or null");
    }

    Set<String> normalizeTraceIds = new LinkedHashSet<>();
    for (String traceId : traceIds.split(",", 1000)) {
      // make sure we have a 16 or 32 character trace ID
      traceId = Span.normalizeTraceId(traceId);
      // Unless we are strict, truncate the trace ID to 64bit (encoded as 16 characters)
      if (!config.getStrictTraceId() && traceId.length() == 32) traceId = traceId.substring(16);
      normalizeTraceIds.add(traceId);
    }
    return response(encodeTraces(getZipkinQueryDAO().getTraces(normalizeTraceIds)));
  }

  private byte[] encodeTraces(List<List<Span>> traces) {
    if (CollectionUtils.isEmpty(traces)) {
      return new byte[] {
          '[',
          ']'
      };
    }
    List<byte[]> encodedTraces = new ArrayList<>(traces.size());
    int tracesSize = traces.size();
    int length = 0;
    for (List<Span> trace : traces) {
      byte[] traceByte = SpanBytesEncoder.JSON_V2.encodeList(trace);
      encodedTraces.add(traceByte);
      length += traceByte.length;
    }
    //bytes length = length + '[' + ']' + join ','
    byte[] allByteArray = new byte[length + 2 + traces.size() - 1];
    ByteBuffer buff = ByteBuffer.wrap(allByteArray);
    buff.put((byte) '[');
    for (int i = 0; i < tracesSize; i++) {
      buff.put(encodedTraces.get(i));
      if (i < tracesSize - 1)
        buff.put((byte) ',');
    }
    buff.put((byte) ']');
    return buff.array();
  }

  private AggregatedHttpResponse response(byte[] body) {
    return AggregatedHttpResponse.of(ResponseHeaders.builder(HttpStatus.OK)
        .contentType(MediaType.JSON)
        .build(), HttpData.wrap(body));
  }

  private IZipkinQueryDAO getZipkinQueryDAO() {
    if (zipkinQueryDAO == null) {
      zipkinQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IZipkinQueryDAO.class);
    }
    return zipkinQueryDAO;
  }
}
