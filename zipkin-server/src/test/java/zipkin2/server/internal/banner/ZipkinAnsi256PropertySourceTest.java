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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.ansi.AnsiOutput;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinAnsi256PropertySourceTest {

  @Before
  public void setUp() throws Exception {
    AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);
  }

  @After
  public void tearDown() throws Exception {
    AnsiOutput.setEnabled(AnsiOutput.Enabled.DETECT);
  }

  @Test
  public void getPropertyFoundShouldConvertAnsiColor() {
    final ZipkinAnsi256PropertySource propertySource = new ZipkinAnsi256PropertySource("test");
    final Object property = propertySource.getProperty("ZipkinAnsi256Color.100");
    assertThat(property).isEqualTo("\033[38;5;100m");
  }

  @Test
  public void getPropertyNotFoundShouldReturnNull() {
    final ZipkinAnsi256PropertySource propertySource = new ZipkinAnsi256PropertySource("test");
    final Object property = propertySource.getProperty("foo");
    assertThat(property).isNull();
  }
}
