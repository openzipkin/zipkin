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
package com.twitter.zipkin.adjuster

import com.twitter.zipkin.common._
import scala.collection.mutable

/**
 * Merge all the spans with the same id. This is used by span stores who store
 * partial spans and need them collated at query time.
 *
 * After merge, spans without a timestamp are filtered out, as they are
 * not possible to present on a timeline. The only scenario where this is
 * possible is when instrumentation sends binary annotations ahead of the span
 * start event, or when a span's start even was lost. Considering this is error
 * -case or transient, there's no option to control this behavior.
 */
object MergeById extends ((Seq[Span]) => List[Span]) {

  override def apply(spans: Seq[Span]): List[Span] = {
    val spanMap = new mutable.HashMap[Long, Span]
    spans.foreach(s => {
      val oldSpan = spanMap.get(s.id)
      oldSpan match {
        case Some(oldS) => {
          val merged = oldS.merge(s)
          spanMap.put(merged.id, merged)
        }
        case None => spanMap.put(s.id, s)
      }
    })
    spanMap.values
      .filter(_.timestamp.nonEmpty)
      .toList.sorted
  }
}
