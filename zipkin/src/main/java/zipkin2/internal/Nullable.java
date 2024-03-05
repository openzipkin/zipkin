/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

/**
 * Libraries such as Guice and AutoValue will process any annotation named {@code Nullable}. This
 * avoids a dependency on one of the many jsr305 jars, causes problems in OSGi and Java 9 projects
 * (where a project is also using jax-ws).
 */
@java.lang.annotation.Documented
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Nullable {
}
