// Copyright 2012 Twitter, Inc.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

// http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie

import java.nio.ByteBuffer
import java.util.{ List => JList, Map => JMap, Set => JSet, ArrayList => JArrayList,HashMap => JHashMap}
import org.apache.cassandra.finagle.thrift


trait BatchMutation {

  private[cassie] val mutations: JMap[ByteBuffer, JMap[String, JList[thrift.Mutation]]] =
    new JHashMap[ByteBuffer, JMap[String, JList[thrift.Mutation]]]()

  // modifies the supplied JHashMap
  protected def putMutation(encodedKey: ByteBuffer, cfName: String, mutation: thrift.Mutation) = {
    var h = mutations.get(encodedKey)
    if (h == null){
      h = new JHashMap[String, JList[thrift.Mutation]]
      mutations.put(encodedKey, h)
    }

    var l = h.get(cfName)
    if (l == null) {
      l = new JArrayList[thrift.Mutation]
      h.put(cfName, l)
    }
    l.add(mutation)
  }

}
