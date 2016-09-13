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
package zipkin.storage.elasticsearch;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import zipkin.DependencyLink;

/** Code resurrected from before we switched to Java 6 as storage components can be Java 7+ */
final class InternalDependencyLinkAdapter extends JsonAdapter<DependencyLink> {
  @Override
  public DependencyLink fromJson(JsonReader reader) throws IOException {
    DependencyLink.Builder result = DependencyLink.builder();
    reader.beginObject();
    while (reader.hasNext()) {
      switch (reader.nextName()) {
        case "parent":
          result.parent(reader.nextString());
          break;
        case "child":
          result.child(reader.nextString());
          break;
        case "callCount":
          result.callCount(reader.nextLong());
          break;
        default:
          reader.skipValue();
      }
    }
    reader.endObject();
    return result.build();
  }

  @Override
  public void toJson(JsonWriter writer, DependencyLink value) throws IOException {
    writer.beginObject();
    writer.name("parent").value(value.parent);
    writer.name("child").value(value.child);
    writer.name("callCount").value(value.callCount);
    writer.endObject();
  }
}