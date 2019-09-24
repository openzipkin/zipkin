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
package zipkin2.server.internal.health;

import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.Component;
import zipkin2.internal.Nullable;

final class ComponentHealth {
  static final String STATUS_UP = "UP", STATUS_DOWN = "DOWN";

  static ComponentHealth ofComponent(Component component) {
    Throwable t = null;
    try {
      CheckResult check = component.check();
      if (!check.ok()) t = check.error();
    } catch (Throwable unexpected) {
      Call.propagateIfFatal(unexpected);
      t = unexpected;
    }
    if (t == null) return new ComponentHealth(component.toString(), STATUS_UP, null);
    String message = t.getMessage();
    String error = t.getClass().getName() + (message != null ? ": " + message : "");
    return new ComponentHealth(component.toString(), STATUS_DOWN, error);
  }

  final String name;
  final String status;
  @Nullable final String error;

  ComponentHealth(String name, String status, String error) {
    this.name = name;
    this.status = status;
    this.error = error;
  }
}
