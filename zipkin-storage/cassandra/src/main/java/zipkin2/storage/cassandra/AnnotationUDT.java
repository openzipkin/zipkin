/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage.cassandra;

import com.datastax.driver.mapping.annotations.UDT;
import java.io.Serializable;
import zipkin2.Annotation;

@UDT(name = "annotation")
final class AnnotationUDT implements Serializable { // for Spark jobs
  static final long serialVersionUID = 0L;

  long ts;
  String v;

  AnnotationUDT() {
    this.ts = 0;
    this.v = null;
  }

  AnnotationUDT(Annotation annotation) {
    this.ts = annotation.timestamp();
    this.v = annotation.value();
  }

  public long getTs() {
    return ts;
  }

  public String getV() {
    return v;
  }

  public void setTs(long ts) {
    this.ts = ts;
  }

  public void setV(String v) {
    this.v = v;
  }

  Annotation toAnnotation() {
    return Annotation.create(ts, v);
  }

  @Override public String toString() {
    return "AnnotationUDT{SpanBytesDecoderts=" + ts + ", v=" + v + "}";
  }
}
