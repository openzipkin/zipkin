/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.common.json

import com.twitter.zipkin.common.Annotation

case class JsonAnnotation (timestamp: String,
                           value: String,
                           host: Option[JsonEndpoint],
                           duration: Option[String]) // Duration in microseconds
  extends WrappedJson


object JsonAnnotation extends JsonWrapper[Annotation] {
  def wrap(a: Annotation) = {
    JsonAnnotation(a.timestamp.toString, a.value, a.host.map(JsonEndpoint.wrap(_)), a.duration map { _.inMicroseconds.toString })
  }
}
