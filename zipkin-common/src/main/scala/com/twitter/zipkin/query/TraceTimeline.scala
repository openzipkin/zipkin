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

import com.twitter.zipkin.common.BinaryAnnotation

/**
 * Query side struct that contains
 * - trace ID
 * - root span (or span closest to the root
 * - sorted list of `TimelineAnnotation`s
 * - binary annotations
 * for a particular trace
 */
case class TraceTimeline(traceId: Long, rootSpanId: Long, annotations: Seq[TimelineAnnotation],
                         binaryAnnotations: Seq[BinaryAnnotation])
