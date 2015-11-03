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
package com.twitter.zipkin.common

import scala.collection.mutable

/**
 * Represents a trace, a bundle of spans.
 */
object Trace {
  def apply(spanTree: SpanTreeEntry): Trace = Trace(spanTree.toList)
}

case class Trace(private val s: Seq[Span]) {

  lazy val spans = mergeBySpanId(s).toList.sorted

  /**
   * Find the root span of this trace and return
   */
  def getRootSpan: Option[Span] = spans.find(!_.parentId.isDefined)

  /**
   * How long did this span take to run?
   * Returns microseconds between start annotation and end annotation
   */
  def duration: Long = {
    val endTs = spans.flatMap(_.annotations).map(_.timestamp).reduceOption(_ max _)
    (endTs.getOrElse(0L) - spans(0).startTs.getOrElse(0L))
  }

  /**
   * Merge all the spans objects with the same span ids into one per id.
   * We store parts of spans in different columns in order to make writes
   * faster and simpler. This means we have to merge them correctly on read.
   */
  private def mergeBySpanId(spans: Iterable[Span]): Iterable[Span] = {
    val spanMap = new mutable.HashMap[Long, Span]
    spans.foreach(s => {
      val oldSpan = spanMap.get(s.id)
      oldSpan match {
        case Some(oldS) => {
          val merged = oldS.mergeSpan(s)
          spanMap.put(merged.id, merged)
        }
        case None => spanMap.put(s.id, s)
      }
    })
    spanMap.values
  }

  /*
   * Turn the Trace into a map of Span Id -> One or more children Spans
   */
  def getIdToChildrenMap: mutable.MultiMap[Long, Span] = {
    val map = new mutable.HashMap[Long, mutable.Set[Span]] with mutable.MultiMap[Long, Span]
    for ( s <- spans; pId <- s.parentId ) map.addBinding(pId, s)
    map
  }

  /*
   * Turn the Trace into a map of Span Id -> Span
   */
  def getIdToSpanMap: Map[Long, Span] =
    spans.map { s => (s.id, s) }.toMap

  /**
   * Get the spans of this trace in a tree form. SpanTreeEntry wraps a Span and it's children.
   */
  def getSpanTree(span: Span, idToChildren: mutable.MultiMap[Long, Span]): SpanTreeEntry = {
    val children = idToChildren.get(span.id)

    children match {
      case Some(cSet) => SpanTreeEntry(span, cSet.map(getSpanTree(_, idToChildren)).toList)
      case None => SpanTreeEntry(span, List[SpanTreeEntry]())
    }
  }
}
