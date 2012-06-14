package com.twitter.zipkin.hadoop

/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 6/13/12
 * Time: 6:45 PM
 * To change this template use File | Settings | File Templates.
 */

import com.twitter.scalding._
import sources.SpanSource
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}
import collection.mutable.HashMap

class PopularKeys(args : Args) extends Job(args) with DefaultDateRangeJob {

  SpanSource()
    .read
    .mapTo(0 -> ('binary_annotations)) { s: Span => (s.binary_annotations.toList ) }
    .filter('binary_annotations){ (a : List[BinaryAnnotation]) => (a != null) && a.size > 0 }
    .mapTo('binary_annotations -> 'keys){ bAnnotations : List[BinaryAnnotation] => bAnnotationToHashMapKeys(bAnnotations) }
//    .groupAll( _.reduce('keys){mergeHashMaps})
    .write(Tsv(args("output")))


  def bAnnotationToHashMapKeys(b : List[BinaryAnnotation]) : HashMap[String, Int] = {
    var map = new HashMap[String, Int]();
    b.foreach({ annotation : BinaryAnnotation =>
      if (annotation.key != null) {
        map += (annotation.key -> 1)
      }
    })
    return map;
  }

  def mergeHashMaps(h1 : HashMap[String, Int], h2 : HashMap[String, Int]) : HashMap[String, Int] = {
    val smaller = if (h1.size > h2.size) h2 else h1
    var larger = if (h1.size > h2.size) h1 else h2
    smaller.foreach({ (annotation : (String, Int)) => if (larger.contains(annotation._1)) larger(annotation._1) += annotation._2 else larger += (annotation._1 -> annotation._2) } )
    return larger
  }
}
