/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.banner;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ansi.AnsiOutput;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class ZipkinBannerTest {
  @AfterEach void tearDown() {
    AnsiOutput.setEnabled(AnsiOutput.Enabled.DETECT);
  }

  @Test void shouldReplaceWhenAnsiEnabled() {
    AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);

    ZipkinBanner banner = new ZipkinBanner();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    banner.printBanner(null, null, new PrintStream(out));

    assertThat(out.toString(UTF_8))
      .doesNotContain("${")
      .contains("\033"); // ansi codes
  }

  @Test void shouldReplaceWhenAnsiDisabled() {
    AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER);

    ZipkinBanner banner = new ZipkinBanner();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    banner.printBanner(null, null, new PrintStream(out));

    assertThat(out.toString(UTF_8))
      .doesNotContain("${")
      .doesNotContain("\033"); // ansi codes
  }
}
