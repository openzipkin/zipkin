
package com.twitter.zipkin.hadoop

/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 6/14/12
 * Time: 4:09 PM
 * To change this template use File | Settcings | File Templates.
 */

import org.specs.Specification
import com.twitter.zipkin.gen
import com.twitter.scalding._
import gen.AnnotationType
import sources.SpanSource
import scala.collection.JavaConverters._
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}
import scala.collection.mutable._
import java.nio._

class PopularKeysSpec extends Specification with TupleConversions {

  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val span = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
    List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava)
  val span1 = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava)


  "PopularKeys" should {
    "return a map with correct entries" in {
      JobTest("com.twitter.zipkin.hadoop.PopularKeys").
        arg("input", "inputFile").
        arg("output", "outputFile").
        arg("date", "2012-01-01T01:00").
        source(SpanSource(), repeatSpan(span, 101, 0) ::: repeatSpan(span1, 50, 200)).
        sink[(String, String, Int)](Tsv("outputFile")) {
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

