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
package zipkin2.server.internal.banner;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.ansi.AnsiOutput;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinBannerTest {
  @After public void tearDown() {
    AnsiOutput.setEnabled(AnsiOutput.Enabled.DETECT);
  }

  @Test public void shouldReplaceWhenAnsiEnabled() {
    AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);

    ZipkinBanner banner = new ZipkinBanner();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    banner.printBanner(null, null, new PrintStream(out));

    assertThat(new String(out.toByteArray(), UTF_8))
      .doesNotContain("${")
      .contains("\033"); // ansi codes
  }

  @Test public void shouldReplaceWhenAnsiDisabled() {
    AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER);

    ZipkinBanner banner = new ZipkinBanner();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    banner.printBanner(null, null, new PrintStream(out));

    assertThat(new String(out.toByteArray(), UTF_8))
      .doesNotContain("${")
      .doesNotContain("\033"); // ansi codes
  }
}
