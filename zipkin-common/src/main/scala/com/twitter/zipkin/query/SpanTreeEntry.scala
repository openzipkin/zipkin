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

import com.twitter.zipkin.common.Span

/**
 * This represents a tree version of a Trace.
 */
case class SpanTreeEntry(span: Span, children: List[SpanTreeEntry]) {

  def toList: List[Span] = {
    childrenToList(this)
  }

  private def childrenToList(span: SpanTreeEntry): List[Span] = {
    if (span.children.isEmpty) {
      List[Span](span.span)
    } else {
      span.span +: span.children.map(childrenToList(_)).flatten
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