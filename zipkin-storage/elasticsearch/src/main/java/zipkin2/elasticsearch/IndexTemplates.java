/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import com.google.auto.value.AutoValue;

@AutoValue
abstract class IndexTemplates {
  static Builder newBuilder() {
    return new AutoValue_IndexTemplates.Builder();
  }

  abstract BaseVersion version();

  abstract char indexTypeDelimiter();

  abstract String span();

  abstract String dependency();

  abstract String autocomplete();

  @AutoValue.Builder
  interface Builder {
    Builder version(BaseVersion version);

    Builder indexTypeDelimiter(char indexTypeDelimiter);

    Builder span(String span);

    Builder dependency(String dependency);

    Builder autocomplete(String autocomplete);

    IndexTemplates build();
  }
}
