/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin2.storage.influxdb;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import zipkin2.CheckResult;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

@AutoValue
public abstract class InfluxDBStorage extends StorageComponent {

  public static Builder newBuilder() {
    return new $AutoValue_InfluxDBStorage.Builder()
      .url("http://localhost:8086")
      .database("zipkin")
      .measurement("zipkin")
      .username("root")
      .password("")
      .retentionPolicy("autogen")
      .strictTraceId(true);
  }

  abstract Builder toBuilder();

  @AutoValue.Builder
  public static abstract class Builder extends StorageComponent.Builder {

    public abstract Builder url(String url);

    public abstract Builder database(String database);

    public abstract Builder measurement(String measurement);

    public abstract Builder username(String username);

    public abstract Builder password(String password);

    public abstract Builder retentionPolicy(String retentionPolicy);

    @Override public abstract Builder strictTraceId(boolean strictTraceId);

    public abstract InfluxDBStorage build();

    Builder() {
    }
  }

  abstract String url();

  abstract String database();

  abstract String measurement();

  abstract String username();

  abstract String password();

  abstract String retentionPolicy();

  abstract boolean strictTraceId();

  /** get and close are typically called from different threads */
  volatile boolean provisioned, closeCalled;

  @Override public SpanStore spanStore() {
    return new InfluxDBSpanStore(this);
  }

  @Override public SpanConsumer spanConsumer() {
    return new InfluxDBSpanConsumer(this);
  }

  @Override public CheckResult check() {
    try {
      get().ping();
      return CheckResult.OK;
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
  }

  @Memoized InfluxDB get() {
    InfluxDB result = InfluxDBFactory.connect(url(), username(), password());
    provisioned = true;
    return result;
  }

  @Override public synchronized void close() {
    if (closeCalled) return;
    if (provisioned) get().close();
    closeCalled = true;
  }

  InfluxDBStorage() {
  }
}
