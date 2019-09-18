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

import org.springframework.boot.ansi.AnsiElement;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

/**
 * This class enable to use Ansi256 Color in the banner txt file
 * via <code>${ZipkinAnsi256Color.XYZ}</code> format (<code>XYZ</code> must be 0-255.).<br>
 * All supported colors can be found <a href="https://en.wikipedia.org/wiki/ANSI_escape_code#8-bit">here</a>.<br>
 * TODO: This class should be deleted when this feature is provided by Spring Boot
 */
public class ZipkinAnsi256ColorPropertySource extends PropertySource<AnsiElement> {

  private static final String PREFIX = "ZipkinAnsi256Color.";

  ZipkinAnsi256ColorPropertySource(String name) {
    super(name);
  }

  @Override
  public Object getProperty(String name) {
    if (StringUtils.hasLength(name)) {
      if (name.startsWith(PREFIX)) {
        int xtermNumber = Integer.parseInt(name.substring(PREFIX.length()));
        return AnsiOutput.encode(new ZipkinAnsi256Color(xtermNumber));
      }
    }
    return null;
  }
}
