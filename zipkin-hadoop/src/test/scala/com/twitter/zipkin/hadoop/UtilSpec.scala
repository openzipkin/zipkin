package com.twitter.zipkin.hadoop

import org.specs.Specification
import sources.Util
import com.twitter.zipkin.gen
import gen.{AnnotationType, Annotation}
import scala.collection.JavaConverters._

class UtilSpec extends Specification {

  "Util.getServiceName" should {
    "yield None if the list is empty" in {
      val l : List[Annotation] = List()
      Util.getServiceName(l) must be_==(None)
    }
    "yield Some(service name) if present" in {
      val endpoint = new gen.Endpoint(123, 666, "service")
      val endpoint1 = new gen.Endpoint(123, 666, "service1")
      val l : List[Annotation] = List(new gen.Annotation(1000, "cr").setHost(endpoint), new gen.Annotation(2000, "ss").setHost(endpoint1))
      Util.getServiceName(l) must be_==(Some("service1"))
    }
  }

  "Util.getBestClientSendName" should {
    "yield client name if parentID == 0" in {
      Util.getBestClientSideName((0, "client", "service")) must be_==("client")
      Util.getBestClientSideName((0, "client", null)) must be_==("client")
    }
    "yield Unknown Service Name if service name is null and pid != 0" in {
      Util.getBestClientSideName((1, "client", null)) must be_==("Unknown Service Name")
    }
    "yield service name otherwise" in {
      Util.getBestClientSideName((1, "client", "service")) must be_==("service")
      Util.getBestClientSideName((1, null, "service")) must be_==("service")
    }
  }

  "Util.repeatSpan" should {
    "repeat a SpanServiceName correctly" in {
      val endpoint = new gen.Endpoint(123, 666, "service")
      val span = new gen.SpanServiceName(12345, "methodcall", 666,
        List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
        List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava, "service").setParent_id(0)
      val span1 = new gen.SpanServiceName(12345, "methodcall", 667,
        List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
        List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava, "service").setParent_id(1)

      Util.repeatSpan(span, 1, 666, 0) must beEqualTo(List((span, 666),(span1, 667)))
    }
    "repeat a Span correctly" in {
      val endpoint = new gen.Endpoint(123, 666, "service")
      val span = new gen.Span(12345, "methodcall", 666,
        List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
        List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava).setParent_id(0)
      val span1 = new gen.Span(12345, "methodcall", 667,
        List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
        List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava).setParent_id(1)

      Util.repeatSpan(span, 1, 666, 0) must beEqualTo(List((span, 666),(span1, 667)))
    }
  }

  "Util.getSpanIDtoNames" should {
    "Get correct (id, service name) pairs" in {
      val endpoint = new gen.Endpoint(123, 666, "service")
      val endpoint1 = new gen.Endpoint(123, 666, "service1")
      val span = new gen.SpanServiceName(12345, "methodcall", 666,
        List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
        List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava,  "service").setParent_id(0)
      val span1 = new gen.SpanServiceName(12345, "methodcall", 667,
        List(new gen.Annotation(1000, "sr").setHost(endpoint1), new gen.Annotation(2000, "cr").setHost(endpoint1)).asJava,
        List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava, "service1").setParent_id(1)
      Util.getSpanIDtoNames(List((span, 666), (span1, 667))) must beEqualTo(List((666, "service"), (667, "service1")))
    }
  }

  "Util.getLevenshteinDistance" should {
    "get correct edit distance when a string is empty" in {
      Util.getLevenshteinDistance("whee", "") must be_==(4)
      Util.getLevenshteinDistance("", "pie") must be_==(3)
    }
    "get correct edit distance when deletions are necessary" in {
      Util.getLevenshteinDistance("hi", "h") must be_==(1)
      Util.getLevenshteinDistance("hihi", "hh") must be_==(2)
    }
    "get correct edit distance when substitutions are necessary" in {
      Util.getLevenshteinDistance("hi", "ho") must be_==(1)
      Util.getLevenshteinDistance("hihi", "heha") must be_==(2)
    }
    "get correct edit distance when additions are necessary" in {
      Util.getLevenshteinDistance("h", "ho") must be_==(1)
      Util.getLevenshteinDistance("hh", "heha") must be_==(2)
    }
    "get correct edit distance in general case" in {
      Util.getLevenshteinDistance("aloha", "BoCa") must be_==(3)
      Util.getLevenshteinDistance("all", "lK") must be_==(2)
    }
  }
}
