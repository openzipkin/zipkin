package com.twitter.zipkin.hadoop


import com.twitter.scalding._
import sources.SpanSource
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}
import collection.mutable.HashMap


class PopularKeys(args : Args) extends Job(args) with DefaultDateRangeJob {

  val preprocessed = SpanSource()
    .read
    .mapTo(0 -> ('trace_id, 'id, 'parent_id, 'annotations, 'binary_annotations))
            { s: Span => (s.trace_id, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
    .groupBy('trace_id, 'id, 'parent_id) { _.reduce('annotations, 'binary_annotations) {
      (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
        (left._1 ++ right._1, left._2 ++ right._2)
      }
    }


  val result = preprocessed
    .project('annotations, 'binary_annotations)
    .filter('binary_annotations){ ba : List[BinaryAnnotation] => (ba != null) && (ba.size > 0)  } //popKeys_2 comments this out
    .flatMap('binary_annotations -> 'key) { ba : List[BinaryAnnotation]  => ba.map{b: BinaryAnnotation => b.key} }  //popKeys_3
   .flatMap('annotations -> ('service)) { annotations: List[Annotation] =>    //popKeys_4
      var hostName: Option[Annotation] = None
      annotations.foreach { a =>
        if (Constants.CLIENT_SEND.equals(a.getValue)) hostName = Some(a)
        if (Constants.SERVER_RECV.equals(a.getValue)) hostName = Some(a)

      }
      for (sr <- hostName)
        yield sr.getHost.service_name
    }
    .groupBy('service, 'key){ _.size('keyCount) } // popKeys_5
    .groupAll( _.sortBy('keyCount) )
    .write(Tsv(args("output")))

}
