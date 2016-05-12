/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.collector.scribe;

import com.facebook.swift.codec.ThriftEnumValue;
import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;
import com.facebook.swift.service.ThriftMethod;
import com.facebook.swift.service.ThriftService;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

@ThriftService("Scribe")
public interface Scribe {

  @ThriftMethod(value = "Log") ListenableFuture<ResultCode> log(
      @ThriftField(value = 1) List<LogEntry> messages);

  enum ResultCode {
    OK(0), TRY_LATER(1);

    final int value;

    ResultCode(int value) {
      this.value = value;
    }

    @ThriftEnumValue
    public int value() {
      return value;
    }
  }

  @ThriftStruct(value = "LogEntry")
  final class LogEntry {

    @ThriftField(value = 1)
    public String category;

    @ThriftField(value = 2)
    public String message;
  }
}
