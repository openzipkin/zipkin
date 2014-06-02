/*
 Copyright 2013 Twitter, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.twitter.zipkin.common

import com.twitter.bijection._
import com.twitter.summingbird.batch.BatchID

object Serialization {
  implicit def kInj[T: Codec]: Injection[(T, BatchID), Array[Byte]] = {
    implicit val buf =
      Bufferable.viaInjection[(T, BatchID), (Array[Byte], Array[Byte])]
    Bufferable.injectionOf[(T, BatchID)]
  }

  implicit def vInj[V: Codec]: Injection[(BatchID, V), Array[Byte]] =
    Injection.connect[(BatchID, V), (V, BatchID), Array[Byte]]

  implicit val mapStrListInj: Injection[Map[String, List[Long]], Array[Byte]] =
    Bufferable.injectionOf[Map[String, List[Long]]]

  implicit val mapStrListTupleInj: Injection[Map[String, (List[Long], List[Long])], Array[Byte]] =
    Bufferable.injectionOf[Map[String, (List[Long], List[Long])]]
}
