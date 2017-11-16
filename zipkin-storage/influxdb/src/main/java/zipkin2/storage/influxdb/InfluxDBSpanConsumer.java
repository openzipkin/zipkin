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

import java.util.List;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;

final class InfluxDBSpanConsumer implements SpanConsumer {
  final InfluxDBStorage influxDB;

  InfluxDBSpanConsumer(InfluxDBStorage influxDB) {
    this.influxDB = influxDB;
  }

  @Override public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);
    throw new UnsupportedOperationException();
  }
}
