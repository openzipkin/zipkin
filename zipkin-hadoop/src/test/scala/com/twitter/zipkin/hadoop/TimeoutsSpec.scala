package com.twitter.zipkin.hadoop

/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 6/14/12
 * Time: 6:10 PM
 * To change this template use File | Settings | File Templates.
 */

import org.specs.Specification
import com.twitter.zipkin.gen
import com.twitter.scalding._
import gen.AnnotationType
import sources.SpanSource
import scala.collection.JavaConverters._
import scala.collection.mutable._

class TimeoutsSpec extends Specification with TupleConversions {
  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val endpoint1 = new gen.Endpoint(1234, 6666, "service1")
  val endpoint2 = new gen.Endpoint(12345, 111, "service2")

  val span = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint), new gen.Annotation(2000, "sr").setHost(endpoint1),
      new gen.Annotation(2001, "finagle.timeout")).asJava,
    List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava)

  val span1 = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint1)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava)

  val span2 = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint1), new gen.Annotation(2000, "sr").setHost(endpoint2),
      new gen.Annotation(2001, "finagle.timeout")).asJava,
    List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava)


  "Timeouts" should {
    "get two timeout things" in {
      JobTest("com.twitter.zipkin.hadoop.Timeouts").
        arg("input", "inputFile").
        arg("output", "outputFile").
        arg("date", "2012-01-01T01:00").
        source(SpanSource(), (repeatSpan(span, 101, 0) ::: (repeatSpan(span1, 20, 200)) ::: (repeatSpan(span2, 30, 400)))).
        sink[(String, String, Long)](Tsv("outputFile")) {
        outputBuffer => outputBuffer foreach { e =>
          println(e)
        }
      }.run.finish
    }

  }

  def repeatSpan(span: gen.Span, count: Int, offset : Int): List[(gen.Span, Int)] = {
    ((0 to count).toSeq map { i: Int => span.deepCopy().setId(i + offset) -> (i + offset)}).toList
  }
}
