/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.query

import com.twitter.util.Future
import com.twitter.zipkin.storage.{Index, IndexedTraceId}
import java.nio.ByteBuffer

sealed trait SliceQuery {
  def execute(index: Index): Future[Seq[IndexedTraceId]]
}
case class SpanSliceQuery(serviceName: String, spanName: String, endTs: Long, limit: Int) extends SliceQuery {
  def execute(index: Index) = {
    index.getTraceIdsByName(serviceName, Some(spanName), endTs, limit)
  }
}
case class AnnotationSliceQuery(serviceName: String, key: String, value: Option[ByteBuffer], endTs: Long, limit: Int) extends SliceQuery {
  def execute(index: Index) = {
    index.getTraceIdsByAnnotation(serviceName, key, value, endTs, limit)
  }
}
