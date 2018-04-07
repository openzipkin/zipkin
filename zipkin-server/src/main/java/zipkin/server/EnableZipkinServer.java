/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin.server;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import zipkin.server.internal.InternalZipkinConfiguration;

/**
 * @deprecated Custom servers are possible, but not supported by the community. Please use our
 * <a href="https://github.com/openzipkin/zipkin#quick-start">default server build</a> first. If you
 * find something missing, please <a href="https://gitter.im/openzipkin/zipkin">gitter</a> us about
 * it before making a custom server.
 *
 * <p>If you decide to make a custom server, you accept responsibility for troubleshooting your
 * build or configuration problems, even if such problems are a reaction to a change made by the
 * OpenZipkin maintainers. In other words, custom servers are possible, but not supported.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(InternalZipkinConfiguration.class)
@Deprecated
public @interface EnableZipkinServer {

}
