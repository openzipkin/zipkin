/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.query;

import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.service.ThriftException;
import com.facebook.swift.service.ThriftMethod;
import com.facebook.swift.service.ThriftService;
import io.zipkin.Trace;
import java.util.List;
import java.util.Set;

@ThriftService("ZipkinQuery")
public interface ZipkinQuery {

  @ThriftMethod(value = "getTraces",
      exception = {
          @ThriftException(type = QueryException.class, id = 1)
      }) List<Trace> getTraces(
      @ThriftField(value = 1) QueryRequest request
  ) throws QueryException;

  @ThriftMethod(value = "getTracesByIds",
      exception = {
          @ThriftException(type = QueryException.class, id = 1)
      }) List<Trace> getTracesByIds(
      @ThriftField(value = 1) List<Long> traceIds,
      @ThriftField(value = 3) boolean adjustClockSkew
  ) throws QueryException;

  @ThriftMethod(value = "getServiceNames",
      exception = {
          @ThriftException(type = QueryException.class, id = 1)
      }) Set<String> getServiceNames() throws QueryException;

  @ThriftMethod(value = "getSpanNames",
      exception = {
          @ThriftException(type = QueryException.class, id = 1)
      }) Set<String> getSpanNames(
      @ThriftField(value = 1) String serviceName
  ) throws QueryException;
}
