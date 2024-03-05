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
import org.springframework.core.type.AnnotatedTypeMetadata;

@Conditional(ConditionalOnThrottledStorage.ThrottledStorageCondition.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface ConditionalOnThrottledStorage {
  class ThrottledStorageCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata a) {
      String throttleEnabled = context.getEnvironment()
              .getProperty("zipkin.storage.throttle.enabled");

      if (!Boolean.valueOf(throttleEnabled)) {
        return ConditionOutcome.noMatch("zipkin.storage.throttle.enabled isn't true");
      }

      return ConditionOutcome.match();
    }
  }
}
