package com.twitter.zipkin.hadoop

import org.specs.Specification
import com.twitter.zipkin.gen
import com.twitter.scalding._
import gen.AnnotationType
import sources.SpanSource
import scala.collection.JavaConverters._
import collection.mutable.HashMap

class DependencyTreeSpec extends Specification with TupleConversions {
  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val endpoint1 = new gen.Endpoint(123, 666, "service1")
  val endpoint2 = new gen.Endpoint(123, 666, "service2")
  val span = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint), new gen.Annotation(2000, "sr").setHost(endpoint)).asJava,
    List[gen.BinaryAnnotation]().asJava)
  val span1 = new gen.Span(123456, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint1), new gen.Annotation(4000, "sr").setHost(endpoint1)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava)
  val span2 = new gen.Span(1234567, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint2), new gen.Annotation(3000, "cr").setHost(endpoint2)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava)

  "DependencyTree" should {
    "Print shit" in {
      JobTest("com.twitter.zipkin.hadoop.DependencyTree")
        .arg("input", "inputFile")
        .arg("output", "outputFile")
        .arg("date", "2012-01-01T01:00")
        .source(SpanSource(), (repeatSpan(span, 30, 40, 0) ++ repeatSpan(span1, 50, 100, 40)))
        .sink[(String, String, Long)](Tsv("outputFile")) {
        val map = new HashMap[String, Long]()
        map("serviceservice") = 0
        map("service1service") = 0
        map("service1service1") = 0
        outputBuffer => outputBuffer foreach { e =>
          println(e)
          map(e._1 + e._2) = e._3
        }
        map("serviceservice") mustEqual 31
        map("service1service") mustEqual 31
        map("service1service1") mustEqual 20
      }.run.finish
    }

  }

  def repeatSpan(span: gen.Span, count: Int, offset : Int, parentOffset : Int): List[(gen.Span, Int)] = {
    ((0 to count).toSeq map { i: Int => span.deepCopy().setId(i + offset).setParent_id(i + parentOffset) -> (i + offset)}).toList
  }
}

