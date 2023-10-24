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

package org.apache.skywalking.zipkin.dependency.entity;

import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import java.util.Objects;

@ScopeDeclaration(id = 10001, name = "ZipkinDependency")
@Stream(name = ZipkinDependency.INDEX_NAME, scopeId = 10001, builder = ZipkinDependency.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@BanyanDB.TimestampColumn(ZipkinDependency.DAY)
public class ZipkinDependency extends Metrics {
  public static final String INDEX_NAME = "zipkin_dependency";
  public static final String DAY = "analyze_day";
  public static final String PARENT = "parent";
  public static final String CHILD = "child";
  public static final String CALL_COUNT = "call_count";
  public static final String ERROR_COUNT = "error_count";

  @Column(name = DAY)
  @BanyanDB.SeriesID(index = 0)
  private long day;
  @Column(name = PARENT)
  @BanyanDB.SeriesID(index = 1)
  private String parent;
  @Column(name = CHILD)
  @BanyanDB.SeriesID(index = 2)
  private String child;
  @Column(name = CALL_COUNT)
  @BanyanDB.MeasureField
  private long callCount;
  @Column(name = ERROR_COUNT)
  @BanyanDB.MeasureField
  private long errorCount;

  @Override
  public boolean combine(Metrics metrics) {
    return true;
  }

  @Override
  public void calculate() {
  }

  @Override
  public Metrics toHour() {
    return null;
  }

  @Override
  public Metrics toDay() {
    return null;
  }

  @Override
  protected StorageID id0() {
    return new StorageID().append(DAY, day)
        .append(PARENT, parent).append(CHILD, child);
  }

  @Override
  public void deserialize(RemoteData remoteData) {
  }

  @Override
  public RemoteData.Builder serialize() {
    // only query from the storage
    return null;
  }

  @Override
  public int remoteHashCode() {
    return (int) this.day;
  }

  public static class Builder implements StorageBuilder<ZipkinDependency> {

    @Override
    public ZipkinDependency storage2Entity(Convert2Entity converter) {
      final ZipkinDependency record = new ZipkinDependency();
      record.setDay(((Number) converter.get(DAY)).longValue());
      record.setParent((String) converter.get(PARENT));
      record.setChild((String) converter.get(CHILD));
      record.setCallCount(((Number) converter.get(CALL_COUNT)).longValue());
      record.setErrorCount(((Number) converter.get(ERROR_COUNT)).longValue());
      return record;
    }

    @Override
    public void entity2Storage(ZipkinDependency entity, Convert2Storage converter) {
      converter.accept(DAY, entity.getDay());
      converter.accept(PARENT, entity.getParent());
      converter.accept(CHILD, entity.getChild());
      converter.accept(CALL_COUNT, entity.getCallCount());
      converter.accept(ERROR_COUNT, entity.getErrorCount());
    }
  }

  public Long getDay() {
    return day;
  }

  public void setDay(Long day) {
    this.day = day;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  public String getChild() {
    return child;
  }

  public void setChild(String child) {
    this.child = child;
  }

  public Long getCallCount() {
    return callCount;
  }

  public void setCallCount(Long callCount) {
    this.callCount = callCount;
  }

  public Long getErrorCount() {
    return errorCount;
  }

  public void setErrorCount(Long errorCount) {
    this.errorCount = errorCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ZipkinDependency)) return false;
    if (!super.equals(o)) return false;
    ZipkinDependency that = (ZipkinDependency) o;
    return getDay() == that.getDay() && Objects.equals(getParent(), that.getParent()) && Objects.equals(getChild(), that.getChild());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getDay(), getParent(), getChild());
  }
}
