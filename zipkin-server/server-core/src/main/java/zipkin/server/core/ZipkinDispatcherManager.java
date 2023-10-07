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

import org.apache.skywalking.oap.server.core.analysis.DispatcherManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteDispatcher;

public class ZipkinDispatcherManager extends DispatcherManager {

  @Override
  public void addIfAsSourceDispatcher(Class aClass) throws IllegalAccessException, InstantiationException {
    if (aClass.getSimpleName().startsWith("Zipkin") || aClass.equals(TagAutocompleteDispatcher.class)) {
      super.addIfAsSourceDispatcher(aClass);
    }
  }
}
