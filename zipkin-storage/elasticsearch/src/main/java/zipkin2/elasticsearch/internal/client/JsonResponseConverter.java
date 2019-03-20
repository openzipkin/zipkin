/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.elasticsearch.internal.client;

import com.squareup.moshi.JsonReader;
import static  okio.Okio.*;

import okio.BufferedSource;
import org.elasticsearch.client.Response;

import java.io.IOException;

/**
 * Converts HTTP response to object using Moshi JSON parser
 *
 * @param <T> Result object type
 */
public abstract class JsonResponseConverter<T> implements ResponseConverter<T> {
  @Override
  public T convert(Response response) throws IOException {
    try (BufferedSource bufferedSource = buffer(source(response.getEntity().getContent()))) {
      return read(bufferedSource);
    }
  }

  protected T read(BufferedSource source) throws IOException {
    try (JsonReader jsonReader = JsonReader.of(source)) {
      return read(jsonReader);
    }
  }

  protected T read(JsonReader jsonReader) throws IOException {
    return null;
  }
}
