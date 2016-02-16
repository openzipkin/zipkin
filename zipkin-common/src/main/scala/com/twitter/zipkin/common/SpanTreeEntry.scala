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
package com.twitter.zipkin.common

import scala.collection.mutable

/**
 * This represents a tree version of a Trace.
 */
object SpanTreeEntry {

  /**
   * Get the spans of this trace in a tree form. SpanTreeEntry wraps a Span and its children.
   */
  def create(span: Span, spans: List[Span]): SpanTreeEntry = { // apply would collide on generics
    create(span, indexByParentId(spans))
  }

  private def create(span: Span, idToChildren: mutable.MultiMap[Long, Span]): SpanTreeEntry = {
    idToChildren.get(span.id) match {
      case Some(cSet) => SpanTreeEntry(span, cSet.map(create(_, idToChildren)).toList)
      case None => SpanTreeEntry(span, List[SpanTreeEntry]())
    }
  }

  /*
   * Turn the Trace into a map of Span Id -> One or more children Spans
   */
  private[common] def indexByParentId(spans: List[Span]): mutable.MultiMap[Long, Span] = {
    val map = new mutable.HashMap[Long, mutable.Set[Span]] with mutable.MultiMap[Long, Span]
    for ( s <- spans; pId <- s.parentId ) map.addBinding(pId, s)
    map
  }
}

case class SpanTreeEntry(span: Span, children: List[SpanTreeEntry]) {

  def toList: List[Span] = childrenToList(this)

  private def childrenToList(entry: SpanTreeEntry): List[Span] = {
    entry.children match {
      case Nil => List[Span](entry.span)
      case children => entry.span :: children.sortBy(_.span).map(childrenToList).flatten
    }
  }

  /**
   * Calculate the depth of the spans in the tree, starting
   * with this span at the specified startDepth
   * @param startDepth Start with this span at this depth
   * @return SpanId to depth level
   */
  def depths(startDepth: Int): Map[Long, Int] = {
    // start out with this span's depth (at startDepth)
    // fold in the childrens depth (increase the current one by 1)
    children.foldLeft(Map(span.id -> startDepth))((prevMap, child) =>
      prevMap ++ child.depths(startDepth + 1)
    )
  }

  /**
   * Print the full trace tree with indentation
   * to give an overview.
   */
  def printTree(indent: Int) {
    println("%s%s".format(" " * indent, span.toString))
    children foreach (s => {
      s.printTree(indent + 2)
    })
  }

}
