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
package io.zipkin.dependencies;

import com.facebook.swift.codec.ThriftConstructor;
import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;
import com.facebook.swift.service.ThriftException;
import com.facebook.swift.service.ThriftMethod;
import com.facebook.swift.service.ThriftService;

import static com.facebook.swift.codec.ThriftField.Requiredness.OPTIONAL;

@ThriftService("DependencyStore")
public interface DependencyStore {
  @ThriftStruct("DependenciesException")
  final class DependenciesException extends RuntimeException {

    private static long serialVersionUID = 1L;

    @ThriftConstructor
    public DependenciesException(@ThriftField(value = 1) String msg) {
      super(msg);
    }

    @Override
    @ThriftField(value = 1)
    public String getMessage() {
      return super.getMessage();
    }
  }

  /**
   * Replaces the links defined for the given interval
   */
  @ThriftMethod(
      value = "storeDependencies",
      exception = @ThriftException(type = DependenciesException.class, id = 1)
  )
  void storeDependencies(
      @ThriftField(value = 1) Dependencies dependencies
  ) throws DependenciesException;

  /**
   * Returns dependency links in an interval contained by start_time and end_time
   *
   * @param startTimestamp microseconds from epoch, defaults to one day before end_time
   * @param endTimestamp microseconds from epoch, defaults to now
   */
  @ThriftMethod(
      value = "getDependencies",
      exception = @ThriftException(type = DependenciesException.class, id = 1)
  )
  Dependencies getDependencies(
      @ThriftField(value = 1, requiredness = OPTIONAL) Long startTimestamp,
      @ThriftField(value = 2, requiredness = OPTIONAL) Long endTimestamp
  ) throws DependenciesException;
}
