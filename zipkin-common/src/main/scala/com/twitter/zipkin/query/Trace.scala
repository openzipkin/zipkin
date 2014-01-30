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
package com.twitter.zipkin.query

import com.twitter.finagle.tracing.{Trace => FTrace}
import com.twitter.logging.Logger
import com.twitter.zipkin.common.{BinaryAnnotation, Endpoint, Span}
import java.nio.ByteBuffer
import scala.collection.mutable

/**
 * A chunk of time, between a start and an end.
 */
case class Timespan(start: Long, end: Long)

/**
 * Represents a trace, a bundle of spans.
 */
object Trace {
  def apply(spanTree: SpanTreeEntry): Trace = Trace(spanTree.toList)
}

case class Trace(private val s: Seq[Span]) {

  lazy val spans = mergeBySpanId(s).toList.sortWith {
    (a, b) =>
      val aTimestamp = a.firstAnnotation.map(_.timestamp).getOrElse(Long.MaxValue)
      val bTimestamp = b.firstAnnotation.map(_.timestamp).getOrElse(Long.MaxValue)
      aTimestamp < bTimestamp
  }

  /**
   * Find the trace id for this trace.
   * Returns none if we have no spans to look up id by
   */
  def id: Option[Long] = {
    spans.headOption.map(_.traceId)
  }

  /**
   * Find the root span of this trace and return
   */
  def getRootSpan: Option[Span] = spans.find {
    s => s.parentId == None
  }

  /**
   * Find a span by the id. Note that this iterates through all the spans.
   */
  def getSpanById(spanId: Long): Option[Span] = spans.find { s => s.id == spanId }

  /**
   * In some cases we don't care if it's the actual root span or just the span
   * that is closes to the root. For example it could be that we don't yet log spans
   * from the root service, then we want the one just below that.
   * FIXME if there are holes in the trace this might not return the correct span
   */
  lazy val getRootMostSpan: Option[Span] = {
    getRootSpan.orElse {
      val idSpan = getIdToSpanMap
      spans.headOption.map {
        s =>
          recursiveGetRootMostSpan(idSpan, s)
      }
    }
  }

  private def recursiveGetRootMostSpan(idSpan: Map[Long, Span], prevSpan: Span): Span = {
    // parent id shouldn't be none as then we would have returned already
    idSpan.get(prevSpan.parentId.get) match {
      case Some(s) => recursiveGetRootMostSpan(idSpan, s)
      case None => prevSpan
    }
  }

  /**
   * Get the start and end timestamps for this trace.
   */
  def getStartAndEndTimestamp: Option[Timespan] = {
    spans.flatMap {
      s => s.annotations.map {
        a => a.timestamp
      }
    } match {
      case Nil => None // No annotations
      case s@_ => Some(Timespan(s.min, s.max))
    }
  }

  /**
   * How long did this span take to run?
   * Returns microseconds between start annotation and end annotation
   */
  def duration: Int = {
    val startEnd = getStartAndEndTimestamp.getOrElse(Timespan(0, 0))
    (startEnd.end - startEnd.start).toInt
  }

  /**
   * Returns all the endpoints involved in this trace.
   */
  def endpoints: Set[Endpoint] = {
    spans.flatMap(s => s.endpoints).toSet
  }

  /**
   * Returns all the services involved in this trace.
   */
  def services: Set[String] = {
    spans.flatMap(_.serviceNames).toSet
  }

  /**
   * Returns a map of services involved in this trace to the
   * number of times they are invoked
   */
  def serviceCounts: Map[String, Int] = {
    spans.flatMap(_.serviceNames).groupBy(s => s).mapValues {
      l: Seq[String] => l.length
    }
  }

  /**
   * Figures out the "span depth". This is used in the ui
   * to figure out how to lay out the spans in the visualization.
   * @return span id -> depth in the tree
   */
  def toSpanDepths: Option[Map[Long, Int]] = {
    FTrace.record("toSpanDepths")
    getRootMostSpan match {
      case None => return None
      case Some(s) => {
        val spanTree = getSpanTree(s, getIdToChildrenMap)
        Some(spanTree.depths(1))
      }
    }
  }

  /**
   * Get all the binary annotations with this key in the whole trace.
   */
  def getBinaryAnnotationsByKey(key: String): Seq[ByteBuffer] = {
    spans.flatMap(_.binaryAnnotations.collect {
      case BinaryAnnotation(bKey, bValue, _, _) if (bKey == key) => bValue
    }.toList)
  }

  /**
   * Get all the binary annotations in this trace.
   */
  def getBinaryAnnotations: Seq[BinaryAnnotation] = {
    spans.map {
      _.binaryAnnotations
    }.flatten
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
    spans.foreach(s => {
      s.parentId match {
        case Some(id) => map.addBinding(id, s)
        case None =>
      }
    })
    map
  }

  /*
   * Turn the Trace into a map of Span Id -> Span
   */
  def getIdToSpanMap: Map[Long, Span] = spans.map {
    s => (s.id, s)
  }.toMap

  /**
   * Get the spans of this trace in a tree form. SpanTreeEntry wraps a Span and it's children.
   */
  def getSpanTree(span: Span, idToChildren: mutable.MultiMap[Long, Span]): SpanTreeEntry = {
    val children = idToChildren.get(span.id)

    children match {
      case Some(cSet) => {
        SpanTreeEntry(span, cSet.map(getSpanTree(_, idToChildren)).toList)
      }
      case None => {
        SpanTreeEntry(span, List[SpanTreeEntry]())
      }
    }
  }

  /**
   * Print the trace tree to give the user an overview.
   */
  def printTraceTree = {
    getRootSpan match {
      case Some(s) => getSpanTree(s, getIdToChildrenMap).printTree(0)
      case None => println("No root node found")
    }
  }
}
