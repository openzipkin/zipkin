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
package zipkin2.server.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import java.io.IOException;
import java.io.Writer;

/**
 * Utilities for working with JSON.
 */
public final class JsonUtil {

  static final JsonFactory JSON_FACTORY = new JsonFactory();
  static final DefaultPrettyPrinter.Indenter TWOSPACES_LF_INDENTER =
    new DefaultIndenter("  ", "\n");

  /**
   * Creates a new {@link JsonGenerator} with pretty-printing enabled forcing {@code '\n'}
   * between lines, as opposed to Jackson's default which uses the system line separator.
   */
  public static JsonGenerator createGenerator(Writer writer) throws IOException {
    JsonGenerator generator = JSON_FACTORY.createGenerator(writer);
    DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
    prettyPrinter.indentArraysWith(TWOSPACES_LF_INDENTER);
    prettyPrinter.indentObjectsWith(TWOSPACES_LF_INDENTER);
    generator.setPrettyPrinter(prettyPrinter);
    return generator;
  }
}
