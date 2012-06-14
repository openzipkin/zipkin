package com.twitter.zipkin.hadoop

import org.specs.Specification
import com.twitter.zipkin.gen
import com.twitter.scalding._
import sources.SpanSource
import scala.collection.JavaConverters._

class ServerResponsetimeSpec extends Specification with TupleConversions {
  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val span = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "ss").setHost(endpoint)).asJava,
    List[gen.BinaryAnnotation]().asJava)


  "ServerResponsetime" should {
    "have no output if input is < 100 entries" in {
      JobTest("com.twitter.zipkin.hadoop.ServerResponsetime").
        arg("input", "inputFile").
        arg("output", "outputFile").
        arg("date", "2012-01-01T01:00").
        source(SpanSource(), List(span -> 0)).
        sink[(String, Int)](Tsv("outputFile")) {
        outputBuffer => outputBuffer.toMap mustEqual Map()
      }.run.finish
    }
    "return one entry with avg 1 ms" in {
      JobTest("com.twitter.zipkin.hadoop.ServerResponsetime").
        arg("input", "inputFile").
        arg("output", "outputFile").
        arg("date", "2012-01-01T01:00").
        source(SpanSource(), repeatSpan(span, 101)).
        sink[(String, String, Double, Double, Double)](Tsv("outputFile")) {
        outputBuffer => outputBuffer foreach { e =>
          e mustEqual ("0.0.0.123", "service", 102d, 1d, 0d)
        }
      }.run.finish
    }

  }

  def repeatSpan(span: gen.Span, count: Int): List[(gen.Span, Int)] = {
    ((0 to count).toSeq map { i: Int => span.deepCopy().setId(i) -> i }).toList
  }
}
