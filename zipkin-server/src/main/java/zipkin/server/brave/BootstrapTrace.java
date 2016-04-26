/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.server.brave;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;

/** This decouples bootstrap tracing from trace initialization. */
public enum BootstrapTrace {
  INSTANCE;

  private final Map<String, Long> annotations = new LinkedHashMap<>();
  private final long timestamp = System.currentTimeMillis() * 1000;
  private final long startTick = System.nanoTime();

  public void record(ApplicationEvent event) {
    annotations.put(event.getClass().getSimpleName().replace("Event", ""), timestamp + microsSinceInit());
    // record duration and flush the trace if we're done
    if (event instanceof ApplicationReadyEvent) {
      long duration = microsSinceInit(); // get duration now, as below logic might skew things.
      ApplicationReadyEvent ready = (ApplicationReadyEvent) event;
      try {
        LocalTracer tracer = ready.getApplicationContext().getBeanFactory()
            .getBean(Brave.class).localTracer();

        tracer.startNewSpan("spring-boot", "bootstrap", timestamp);
        annotations.forEach(tracer::submitAnnotation);
        tracer.finishSpan(duration);
      } catch (NoSuchBeanDefinitionException ignored) {
        // Brave is optional
      }
    }
  }

  private long microsSinceInit() {
    return (System.nanoTime() - startTick) / 1000;
  }
}
