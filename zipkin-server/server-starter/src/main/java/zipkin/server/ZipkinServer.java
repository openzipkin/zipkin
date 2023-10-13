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
package zipkin.server;

import com.google.common.io.CharStreams;
import org.apache.skywalking.oap.server.core.version.Version;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

import java.io.Reader;

public class ZipkinServer {
  public static void main(String[] args) {
    printBanner();
    ZipkinServerBootstrap.start();
  }

  private static void printBanner() {
    try (Reader applicationReader = ResourceUtils.read("zipkin.txt")) {
      String banner = CharStreams.toString(applicationReader);

      banner = banner.replace("${AnsiOrange}", "\u001b[38;5;208m"); // Ansi 256 color code 208 (orange)
      banner = banner.replace("${AnsiNormal}", "\u001b[0m");
      banner = banner.replace("${ProjectVersion}", Version.CURRENT.getBuildVersion());
      banner = banner.replace("${GitCommitID}", Version.CURRENT.getCommitId());

      System.out.println(banner);
    } catch (Exception ex) {
      // who cares
    }
  }
}
