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
package com.twitter.zipkin.query.adjusters

import com.twitter.zipkin.common.{Span, SpanTreeEntry}

trait AdjusterMessage
case class AdjusterWarning(msg: String) extends AdjusterMessage

object AdjusterSpanTreeEntry {

  def apply(spanTree: SpanTreeEntry): AdjusterSpanTreeEntry = {
    new AdjusterSpanTreeEntry(spanTree.span, spanTree.children.map {AdjusterSpanTreeEntry(_)}, Seq())
  }
}

/**
 * Subclass of SpanTreeEntry that also holds AdjusterMessages
 * Used by adjusters to propagate messages up the tree when making adjustments
 */
class AdjusterSpanTreeEntry (
  override val span: Span,
  override val children: List[AdjusterSpanTreeEntry],
  val messages: Seq[AdjusterMessage]
)
extends SpanTreeEntry(span, children)