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
package io.zipkin.scribe;

import com.facebook.swift.codec.ThriftConstructor;
import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;

@ThriftStruct(value = "LogEntry", builder = LogEntry.Builder.class)
public final class LogEntry {

  @ThriftField(value = 1)
  public final String category;

  @ThriftField(value = 2)
  public final String message;

  LogEntry(String category, String message) {
    this.category = category;
    this.message = message;
  }

  public static final class Builder {
    private String category;
    private String message;

    public Builder() {
    }

    @ThriftField(value = 1)
    public LogEntry.Builder category(String category) {
      this.category = category;
      return this;
    }

    @ThriftField(value = 2)
    public LogEntry.Builder message(String message) {
      this.message = message;
      return this;
    }

    @ThriftConstructor
    public LogEntry build() {
      return new LogEntry(category, message);
    }
  }
}
