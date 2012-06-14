
package com.twitter.zipkin.hadoop

import com.twitter.scalding._
import sources.SpanSource
import com.twitter.zipkin.gen.{Span, Constants, Annotation}

/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 6/12/12
 * Time: 4:55 PM
 * To change this template use File | Settings | File Templates.
 */

class DependencyTree(args: Args) extends Job(args) with DefaultDateRangeJob {
  //(ID, ParentID, Service, Duration)
  val spanInfo = SpanSource()
    .read
    // only need id and annotations for this
    .mapTo(0 -> ('id, 'parent_id, 'annotations)) { s: Span => (s.id, s.parent_id, s.annotations.toList) }
    .groupBy('id, 'parent_id) { data =>
  // merge annotations from all span objects into one list
    data.reduce('annotations) {
      (annotations: List[Annotation], serverAnnotations: List[Annotation]) =>
      //we only care about server annotations
        val filtered = annotations.filter((a) => serverAnnotations.contains(a.getValue))
        filtered ++ serverAnnotations
      }
  }
    .flatMap('annotations -> ('cService, 'sService)) { annotations: List[Annotation] =>
      var clientSend: Option[Annotation] = None
      var serverReceived: Option[Annotation] = None
      annotations.foreach { a =>
        if (Constants.CLIENT_SEND.equals(a.getValue)) clientSend = Some(a)
        if (Constants.SERVER_RECV.equals(a.getValue)) serverReceived = Some(a)
      }
      // only return a value if we have both annotations
      for (cs <- clientSend; sr <- serverReceived)
      yield (cs.getHost.service_name, sr.getHost.service_name)
    }

    // get (ID, ServiceName)
    val idName = spanInfo
      .project('id, 'sService)
      .unique('id, 'sService)
      .rename('id, 'id1)
      .rename('sService, 'parentService)

    val spanInfoWithParent = spanInfo
      .joinWithSmaller('parent_id -> 'id1, idName)
      .groupBy('sService, 'parentService){ _.size('count) }
      .write(Tsv(args("output")))
}
