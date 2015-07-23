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
package com.twitter.zipkin.query.conversions

import java.nio.ByteBuffer

import com.twitter.zipkin.common._
import com.twitter.zipkin.query.{TimelineAnnotation, Trace, TraceTimeline}
import com.twitter.zipkin.thriftscala
import org.scalatest.{FunSuite, Matchers}

class TraceTimelineSpec extends FunSuite with Matchers {

//T = 0	 koalabird-cuckoo	 ValuesFromSource	 Server receive	 10.34.238.111 ():9149
//T + 1	 client	 multiget_slice	 Client send	 10.34.238.111 ():54147
//T + 1	 koalabird-cuckoo	 ValuesFromSource	 Client send	 10.34.94.110 ():36516
//T + 85	 koalabird-cuckoo	 ValuesFromSource	 Server send	 10.34.238.111 ():9149
//T + 85	 client	 multiget_slice	 Client receive	 10.34.238.111 ():54147
//T + 87	 koalabird-cuckoo	 ValuesFromSource	 Client receive	 10.34.94.110 ():36516

//The order the must have actually happened + correct service name
//T + 1	 koalabird-cuckoo	 ValuesFromSource	 Client send	 10.34.94.110 ():36516
//T = 0	 cuckoo.thrift	 ValuesFromSource	 Server receive	 10.34.238.111 ():9149
//T + 1	 cassie	 multiget_slice	 Client send	 10.34.238.111 ():54147
//T + 85	 cassie	 multiget_slice	 Client receive	 10.34.238.111 ():54147
//T + 85	 cuckoo.thrift	 ValuesFromSource	 Server send	 10.34.238.111 ():9149
//T + 87	 koalabird-cuckoo	 ValuesFromSource	 Client receive	 10.34.94.110 ():36516

//  Trace(spans=[
//    Span(name='ValuesFromSource', service_name='koalabird-cuckoo', traceId=2209720933601260005L, binaryAnnotations={}, annotations=[
//      Annotation(timestamp=1315417949643000L, host=Endpoint(ipv4=170024558, port=-29020), value='cs'),
//      Annotation(timestamp=1315417949729000L, host=Endpoint(ipv4=170024558, port=-29020), value='cr'),
//      Annotation(timestamp=1315417949642000L, host=Endpoint(ipv4=170061423, port=9149), value='sr'),
//      Annotation(timestamp=1315417949727000L, host=Endpoint(ipv4=170061423, port=9149), value='ss')], parentId=None, id=2209720933601260005L),
//    Span(name='multiget_slice', service_name='client', traceId=2209720933601260005L, binaryAnnotations={}, annotations=[
//      Annotation(timestamp=1315417949643000L, host=Endpoint(ipv4=170061423, port=-11389), value='cs'),
//      Annotation(timestamp=1315417949727000L, host=Endpoint(ipv4=170061423, port=-11389), value='cr')], parentId=2209720933601260005L, id=-855543208864892776L)])

  val cuckooName = "cuckoo.thrift"
  val koalabirdName = "koalabird-cuckoo"
  val cassieName = "cassie"

  val endpoint1 = Some(Endpoint(1, 1, cuckooName)) //9149
  val endpoint2 = Some(Endpoint(2, 2, cassieName)) //54147
  val endpoint3 = Some(Endpoint(3, 3, koalabirdName)) //36516

  val et1 = endpoint1.get
  val et2 = endpoint2.get
  val et3 = endpoint3.get

  // This is from a real trace, at least what the data would look like
  // after being run through the TimeSkewAdjuster
  val ann1 = Annotation(1, thriftscala.Constants.SERVER_RECV, endpoint1)
  val ann2 = Annotation(1, thriftscala.Constants.CLIENT_SEND, endpoint2)
  val ann3 = Annotation(1, thriftscala.Constants.CLIENT_SEND, endpoint3)
  val ann4 = Annotation(86, thriftscala.Constants.SERVER_SEND, endpoint1)
  val ann5 = Annotation(85, thriftscala.Constants.CLIENT_RECV, endpoint2)
  val ann6 = Annotation(87, thriftscala.Constants.CLIENT_RECV, endpoint3)

  val ba1 = BinaryAnnotation("key1", ByteBuffer.wrap("value1".getBytes), AnnotationType.String, None)

  val span1 = Span(1, "ValuesFromSource", 2209720933601260005L, None,
    List(ann3, ann6), List(ba1))
  val span2 = Span(1, "ValuesFromSource", 2209720933601260005L, None,
    List(ann4, ann1), Nil)
  // the above two spans are part of the same actual span

  val span3 = Span(1, "multiget_slice", -855543208864892776L, Some(2209720933601260005L),
    List(ann5, ann2), Nil)
  val trace = new Trace(List(span1, span2, span3))

  // annotation numbers match those above, order in list should not though
  val tAnn1 = TimelineAnnotation(1, thriftscala.Constants.SERVER_RECV, et1,
    2209720933601260005L, None, cuckooName, "ValuesFromSource")
  val tAnn2 = TimelineAnnotation(1, thriftscala.Constants.CLIENT_SEND, et2,
    -855543208864892776L, Some(2209720933601260005L), cassieName, "multiget_slice")
  val tAnn3 = TimelineAnnotation(1, thriftscala.Constants.CLIENT_SEND, et3,
    2209720933601260005L, None, koalabirdName, "ValuesFromSource")
  val tAnn4 = TimelineAnnotation(86, thriftscala.Constants.SERVER_SEND, et1,
    2209720933601260005L, None, cuckooName, "ValuesFromSource")
  val tAnn5 = TimelineAnnotation(85, thriftscala.Constants.CLIENT_RECV, et2,
    -855543208864892776L, Some(2209720933601260005L), cassieName, "multiget_slice")
  val tAnn6 = TimelineAnnotation(87, thriftscala.Constants.CLIENT_RECV, et3,
    2209720933601260005L, None, koalabirdName, "ValuesFromSource")

  val expectedTimeline = TraceTimeline(1, 2209720933601260005L, List(tAnn3, tAnn1, tAnn2,
    tAnn5, tAnn4, tAnn6), List(ba1))

  test("convert to timeline with correct annotations ordering") {
    val actualTimeline = TraceTimeline(trace)
    actualTimeline should be (Some(expectedTimeline))
  }

  test("return none if empty trace") {
    val actualTimeline = TraceTimeline(new Trace(List()))
    actualTimeline should be (None)
  }
}
