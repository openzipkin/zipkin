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
import com.google.auto.value.AutoValue;

/**
 * Moments is defined as below per algebird's MomentsGroup.scala
 *
 * A class to calculate the first five central moments over a sequence of Doubles. Given the first
 * five central moments, we can then calculate metrics like skewness and kurtosis.
 *
 * m{i} denotes the ith central moment.
 */
@AutoValue
@ThriftStruct(value = "Moments", builder = AutoValue_Moments.Builder.class)
public abstract class Moments {

  public static Builder builder() {
    return new AutoValue_Moments.Builder();
  }

  public static Builder builder(Moments source) {
    return new AutoValue_Moments.Builder(source);
  }

  /** count */
  @ThriftField(value = 1)
  public abstract long m0();

  /** mean */
  @ThriftField(value = 2)
  public abstract double m1();

  /** population variance = m2 / count, when count > 1 */
  @ThriftField(value = 3)
  public abstract double m2();

  /** skewness = math.sqrt(count) * m3 / math.pow(m2, 1.5), when count > 2 */

  @ThriftField(value = 4)
  public abstract double m3();

  /** kurtosis = count * m4 / math.pow(m2, 2) - 3, when count > 3 */
  @ThriftField(value = 5)
  public abstract double m4();

  @AutoValue.Builder
  public interface Builder {

    @ThriftField(value = 1) Builder m0(long m0);

    @ThriftField(value = 2) Builder m1(double m1);

    @ThriftField(value = 3) Builder m2(double m2);

    @ThriftField(value = 4) Builder m3(double m3);

    @ThriftField(value = 5) Builder m4(double m4);

    @ThriftConstructor Moments build();
  }
}
