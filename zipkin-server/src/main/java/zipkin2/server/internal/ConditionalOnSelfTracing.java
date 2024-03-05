/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * This helps solve a number of problems including the following, which sometimes only break in an
 * alpine JRE. The solution is to go with pure properties instead.
 *
 * <pre>
 * <p>ConditionalOnClass(name = "brave.Tracing")
 * <p>ConditionalOnBean(Brave.class)
 * </pre>
 */
@Conditional(ConditionalOnSelfTracing.SelfTracingCondition.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ConditionalOnSelfTracing {
  String storageType() default "";

  class SelfTracingCondition extends SpringBootCondition {
    static final boolean BRAVE_PRESENT = checkForBrave();

    static boolean checkForBrave() {
      try {
        Class.forName("brave.Tracing");
        return true;
      } catch (ClassNotFoundException e) {
        return false;
      }
    }

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata a) {
      if (!BRAVE_PRESENT) {
        return ConditionOutcome.noMatch("Brave must be in the classpath");
      }

      String selfTracingEnabled = context.getEnvironment()
          .getProperty("zipkin.self-tracing.enabled");

      if (!Boolean.valueOf(selfTracingEnabled)) {
        return ConditionOutcome.noMatch("zipkin.self-tracing.enabled isn't true");
      }

      String expectedStorageType = AnnotationAttributes.fromMap(
          a.getAnnotationAttributes(ConditionalOnSelfTracing.class.getName())
      ).getString("storageType");

      if (expectedStorageType.isEmpty()) {
        return ConditionOutcome.match();
      }

      String storageType = context.getEnvironment().getProperty("zipkin.storage.type");
      return expectedStorageType.equals(storageType) ?
          ConditionOutcome.match() :
          ConditionOutcome.noMatch(
              "zipkin.storage.type was: " + storageType + " expected " + expectedStorageType);
    }
  }
}
