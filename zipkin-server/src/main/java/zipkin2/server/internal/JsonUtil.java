/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
