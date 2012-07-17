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

package com.twitter.zipkin.hadoop.sources

import java.nio.ByteBuffer
import java.util.Arrays
import com.twitter.zipkin.gen.{Constants, Annotation}
import com.twitter.zipkin.gen

/**
 * A collection of useful functions used throughout the library
 */

object Util {

  val UNKNOWN_SERVICE_NAME = "Unknown Service Name"
  val ERROR_MESSAGE = "500 Internal Server Error"

  /**
   * Given a byte buffer, produces the array of bytes it represents
   * Stolen from http://svn.apache.org/viewvc/cassandra/trunk/src/java/org/apache/cassandra/utils/ByteBufferUtil.java?revision=1201726&view=markup
   * @param buf the byte buffer we need to convert to an array
   * @return the array encoding the same information as the buffer
   */
  def getArrayFromBuffer(buf: ByteBuffer): Array[Byte] = {
    val length = buf.remaining
    if (buf.hasArray()) {
      val boff = buf.arrayOffset() + buf.position()
      if (boff == 0 && length == buf.array().length) {
        buf.array()
      } else {
        Arrays.copyOfRange(buf.array(), boff, boff + length)
      }
    } else {
      val bytes = new Array[Byte](length)
      buf.duplicate.get(bytes)
      bytes
    }
  }

  /**
   * Given a list of annotations, finds the best possible service name
   * @param annotations a list of Annotations
   * @return Some(service name) if a service name exists, None otherwise
   */
  def getServiceName(annotations : List[Annotation]) : Option[String] = {
    var service: Option[Annotation] = None
    var hasServerRecv = false
    annotations.foreach { a : Annotation =>
      if ((Constants.CLIENT_SEND.equals(a.getValue) || Constants.CLIENT_RECV.equals(a.getValue))) {
        if ((a.getHost != null) && !hasServerRecv) {
          service = Some(a)
        }
      }
      if ((Constants.SERVER_RECV.equals(a.getValue) || Constants.SERVER_SEND.equals(a.getValue))) {
        if (a.getHost != null) {
          service = Some(a)
          hasServerRecv = true
        }
      }
    }
    for (s <- service)
      yield s.getHost.service_name
  }

  /**
   * Given a parentId, a client name, and a service name, finds the best client side name possible for the endpoint this is the
   * information for
   * @param parentInfo A tuple containing a parentID, the client name, and the parent's name
   * @return the best client side name for the endpoint
   */
  def getBestClientSideName(parentInfo : (Long, String, String)) : String = {
    parentInfo match {
      case (0, cName, _) => cName
      case (_, _, null) => UNKNOWN_SERVICE_NAME
      case (_, _, pName) => pName
      case _ => UNKNOWN_SERVICE_NAME
    }
  }

  /**
   * Given a SpanServiceName, repeats it count number of times starting with ID at offset and parentID at parentOffset, both
   * incremented by one each copy
   * @param span a SpanServiceName
   * @param count the number of times the span is to be duplicated
   * @param offset the initial offset of the span's copies's IDs
   * @param parentOffset the initial offset of the span's copies's parent IDs
   * @return a List of tuples, whose first elements are SpanServiceNames that copy the information of the span passed, and the
   *         second element is the span ID of the copy.
   */
  def repeatSpan(span: gen.SpanServiceName, count: Int, offset : Int, parentOffset : Int): List[(gen.SpanServiceName, Int)] = {
    ((0 to count).toSeq map { i: Int => span.deepCopy().setId(i + offset).setParent_id(i + parentOffset) -> (i + offset)}).toList
  }

  /**
   * Given a Span, repeats it count number of times starting with ID at offset and parentID at parentOffset, both
   * incremented by one each copy
   * @param span a Span
   * @param count the number of times the span is to be duplicated
   * @param offset the initial offset of the span's copies's IDs
   * @param parentOffset the initial offset of the span's copies's parent IDs
   * @return a List of tuples, whose first elements are Span that copy the information of the span passed, and the
   *         second element is the span ID of the copy.
   */
  def repeatSpan(span: gen.Span, count: Int, offset : Int, parentOffset : Int): List[(gen.Span, Int)] = {
    ((0 to count).toSeq map { i: Int => span.deepCopy().setId(i + offset).setParent_id(if (parentOffset == -1) 0 else i + parentOffset) -> (i + offset)}).toList
  }

  /**
   * Given a list of spans and their IDs, creates a list of (id, service name)
   * @param spans a list of (Span, SpanID)
   * @return a list of (SpanID, service name)
   */
  def getSpanIDtoNames(spans : List[(gen.SpanServiceName, Int)]) : List[(Long, String)] = {
    spans.map { s : (gen.SpanServiceName, Int) => {
        val (span, _) = s
        (span.id, span.service_name)
      }
    }
  }

  /**
   * Given two strings, finds the minimum edit distance between them given only substitutions, deletions, and additions with the
   * Levenshtein algorithm.
   * @param s1 a String
   * @param s2 another String
   * @return the edit distance between them after they're converted to lower case.
   */
  def getLevenshteinDistance(s1 : String, s2 : String) : Int = {
    val shorter = if (s1.length() > s2.length() ) s2.toLowerCase else s1.toLowerCase
    val longer = if (s1.length() > s2.length() ) s1.toLowerCase else s2.toLowerCase

    var lastAndCurrentRow = ((0 to shorter.length() ).toArray, new Array[Int](shorter.length() + 1))
    for (i <- 1 to longer.length()) {
      val (lastRow, currentRow) = lastAndCurrentRow
      currentRow(0) = i
      for (j <- 1 to shorter.length()) {
        if (longer.charAt(i - 1) == shorter.charAt(j - 1)) {
          // they match; edit distance stays the same
          currentRow(j) = lastRow(j - 1)
        } else {
          // they differ; edit distance is the min of substitution, deletion, and addition
          currentRow(j) = math.min(math.min( lastRow(j), currentRow(j - 1) ), lastRow(j - 1)) + 1
        }
      }
      lastAndCurrentRow = lastAndCurrentRow.swap
    }
    return lastAndCurrentRow._1(shorter.length())
  }
}