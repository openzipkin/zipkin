/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.Component;
import zipkin2.internal.ClosedComponentException;
import zipkin2.internal.Nullable;

final class ComponentHealth {
  static final Logger LOGGER = LoggerFactory.getLogger(ComponentHealth.class);
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
    String error;
    if (t instanceof ClosedComponentException) {
      error = message;
    } else {
      error = t.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }
    LOGGER.debug(error);
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
