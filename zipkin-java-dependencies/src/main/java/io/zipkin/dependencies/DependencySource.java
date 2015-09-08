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

import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.service.ThriftException;
import com.facebook.swift.service.ThriftMethod;
import com.facebook.swift.service.ThriftService;

import static com.facebook.swift.codec.ThriftField.Requiredness.OPTIONAL;

@ThriftService("DependencySource")
public interface DependencySource {

  /**
   * Get an aggregate representation of all services paired with every service they call in to.
   * This includes information on call counts and mean/stdDev/etc of call durations.  The two arguments
   * specify epoch time in microseconds. The end time is optional and defaults to one day after the
   * start time.
   */
  @ThriftMethod(value = "getDependencies",
      exception = {
          @ThriftException(type = DependenciesException.class, id = 1)
      })
  Dependencies getDependencies(
      @ThriftField(value = 1, requiredness = OPTIONAL) Long startTime,
      @ThriftField(value = 2, requiredness = OPTIONAL) Long endTime
  ) throws DependenciesException;
}
