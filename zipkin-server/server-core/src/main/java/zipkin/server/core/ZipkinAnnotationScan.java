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
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;
import org.apache.skywalking.oap.server.core.storage.StorageException;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class ZipkinAnnotationScan {
  private final List<AnnotationListenerCache> listeners;

  public ZipkinAnnotationScan() {
    this.listeners = new LinkedList<>();
  }

  /**
   * Register the callback listener
   *
   * @param listener to be called after class found w/ annotation
   */
  public void registerListener(AnnotationListener listener) {
    listeners.add(new AnnotationListenerCache(listener));
  }

  public void scan() throws IOException, StorageException {
    ClassGraph classGraph = new ClassGraph();
    classGraph.enableClassInfo();
    final ScanResult scan = classGraph.scan();
    for (ClassInfo classInfo : scan.getAllClasses()) {
      // not skywalking package or a subclass should ignore
      if (!classInfo.getName().startsWith("org.apache.skywalking") || classInfo.getName().contains("$")) {
        continue;
      }
      final Class<?> aClass = classInfo.loadClass();
      for (AnnotationListenerCache listener : listeners) {
        if (aClass.isAnnotationPresent(listener.annotation())) {
          listener.addMatch(aClass);
        }
      }
    }

    for (AnnotationListenerCache listener : listeners) {
      listener.complete();
    }
  }

  private class AnnotationListenerCache {
    private AnnotationListener listener;
    private List<Class<?>> matchedClass;

    private AnnotationListenerCache(AnnotationListener listener) {
      this.listener = listener;
      matchedClass = new LinkedList<>();
    }

    private Class<? extends Annotation> annotation() {
      return this.listener.annotation();
    }

    private void addMatch(Class aClass) {
      matchedClass.add(aClass);
    }

    private void complete() throws StorageException {
      matchedClass.sort(Comparator.comparing(Class::getName));
      for (Class<?> aClass : matchedClass) {
        listener.notify(aClass);
      }
    }
  }

}
