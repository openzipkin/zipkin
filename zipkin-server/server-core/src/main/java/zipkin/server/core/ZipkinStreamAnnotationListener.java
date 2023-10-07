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

import org.apache.skywalking.oap.server.core.analysis.StreamAnnotationListener;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

public class ZipkinStreamAnnotationListener extends StreamAnnotationListener {

  public ZipkinStreamAnnotationListener(ModuleDefineHolder moduleDefineHolder) {
    super(moduleDefineHolder);
  }

  @Override
  public void notify(Class aClass) throws StorageException {
    // only including all zipkin streaming
    if (aClass.getSimpleName().startsWith("Zipkin") || aClass.equals(TagAutocompleteData.class)) {
      super.notify(aClass);
    }
  }
}
