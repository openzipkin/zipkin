/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.core;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.apache.skywalking.oap.server.core.analysis.DispatcherManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteDispatcher;
import org.apache.skywalking.oap.server.core.zipkin.dispatcher.ZipkinSpanRecordDispatcher;

import java.io.IOException;

public class ZipkinDispatcherManager extends DispatcherManager {
  private final boolean searchEnable;

  public ZipkinDispatcherManager(boolean searchEnable) {
    this.searchEnable = searchEnable;
  }

  @Override
  public void scan() throws IOException, IllegalAccessException, InstantiationException {
    ClassGraph classGraph = new ClassGraph();
    classGraph.enableClassInfo();
    final ScanResult scan = classGraph.scan();
    for (ClassInfo classInfo : scan.getAllClasses()) {
      // not skywalking package or a subclass should ignore
      if (!classInfo.getName().startsWith("org.apache.skywalking") || classInfo.getName().contains("$")) {
        continue;
      }
      final Class<?> aClass = classInfo.loadClass();
      addIfAsSourceDispatcher(aClass);
    }
  }

  @Override
  public void addIfAsSourceDispatcher(Class aClass) throws IllegalAccessException, InstantiationException {
    if (aClass.getSimpleName().startsWith("Zipkin") || aClass.equals(TagAutocompleteDispatcher.class)) {
      // If search is disabled, only the span can be stored.
      if (!searchEnable && !aClass.equals(ZipkinSpanRecordDispatcher.class)) {
        return;
      }
      super.addIfAsSourceDispatcher(aClass);
    }
  }
}
