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

package zipkin.server.storage.cassandra;

import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

import java.util.ArrayList;
import java.util.List;

public class CQLExecutor implements InsertRequest, UpdateRequest {
  private final String cql;
  private final List<Object> params;
  private final SessionCacheCallback callback;
  private List<CQLExecutor> additionalCQLs;

  public CQLExecutor(String cql, List<Object> params, SessionCacheCallback callback, List<CQLExecutor> additionalCQLs) {
    this.cql = cql;
    this.params = params;
    this.callback = callback;
    this.additionalCQLs = additionalCQLs;
  }

  public void appendAdditionalCQLs(List<CQLExecutor> cqlExecutors) {
    if (additionalCQLs == null) {
      additionalCQLs = new ArrayList<>();
    }
    additionalCQLs.addAll(cqlExecutors);
  }

  @Override
  public String toString() {
    return cql;
  }

  @Override
  public void onInsertCompleted() {
    if (callback != null)
      callback.onInsertCompleted();
  }

  @Override
  public void onUpdateFailure() {
    if (callback != null)
      callback.onUpdateFailure();
  }

  public List<CQLExecutor> getAdditionalCQLs() {
    return additionalCQLs;
  }

  public String getCql() {
    return cql;
  }

  public List<Object> getParams() {
    return params;
  }
}
