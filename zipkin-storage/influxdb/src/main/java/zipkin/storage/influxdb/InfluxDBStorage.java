/**
 * Copyright 2016-2017 The OpenZipkin Authors
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
package zipkin.storage.influxdb;

import org.influxdb.InfluxDB;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

import java.util.List;

public class InfluxDBStorage extends StorageComponent implements SpanStore, SpanConsumer {

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder extends StorageComponent.Builder {
    boolean strictTraceId = true;
    String address;
    String password;
    String username;
    String retentionPolicy;
    String database;
    String measurement;

    /**
     * {@inheritDoc}
     */
    @Override
    public Builder strictTraceId(boolean strictTraceId) {
      this.strictTraceId = strictTraceId;
      return this;
    }

    public Builder address(String address) {
      this.address = address;
      return this;
    }
    public Builder password(String password) {
      this.password = password;
      return this;
    }
    public Builder username(String username) {
      this.username = username;
      return this;
    }
    public Builder retentionPolicy(String retentionPolicy) {
      this.retentionPolicy = retentionPolicy;
      return this;
    }
    public Builder database(String database) {
      this.database = database;
      return this;
    }
    public Builder measurement(String measurement) {
      this.measurement = measurement;
      return this;
    }

    @Override
    public InfluxDBStorage build() {
      return new InfluxDBStorage();
    }

    Builder() {
    }
  }

  @Override
  public Call<Void> accept(List<Span> spans) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Call<List<List<Span>>> getTraces(QueryRequest request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Call<List<String>> getServiceNames() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    //  select count("duration") from zipkin where time > now() - 30m and time < now() group by "id","parent_id", time(1d)
    throw new UnsupportedOperationException();
  }

  @Override
  public Call<List<String>> getSpanNames(String serviceName) {
    // show tag values with key="serviceName"
    throw new UnsupportedOperationException();
  }

  @Override
  public Call<List<Span>> getTrace(String traceId) {
    // SELECT * from zipkin where "trace_id"='traceId'
    throw new UnsupportedOperationException();
  }

  @Override
  public SpanStore spanStore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public SpanConsumer spanConsumer() {
    throw new UnsupportedOperationException();
  }

  InfluxDBStorage() {
  }
}
