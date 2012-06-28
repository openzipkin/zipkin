package com.twitter.zipkin.hadoop

import org.specs.Specification
import sources.Util
import com.twitter.zipkin.gen
import gen.{AnnotationType, Annotation}
import scala.collection.JavaConverters._

class UtilSpec extends Specification {

  "Util.getClientAndServiceName" should {
    "yield None if the list is empty" in {
      val l : List[Annotation] = List()
      Util.getClientAndServiceName(l) must be_==(None)
    }
    "yield (null, service name) if there is no client side name" in {
      val endpoint = new gen.Endpoint(123, 666, "service")
      val l : List[Annotation] = List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "ss").setHost(endpoint))
      Util.getClientAndServiceName(l) must be_==(Some(null, "service"))

    }
    "yield (client name, service name) if both are present" in {
      val endpoint = new gen.Endpoint(123, 666, "service")
      val endpoint1 = new gen.Endpoint(123, 666, "service1")
      val l : List[Annotation] = List(new gen.Annotation(1000, "cr").setHost(endpoint), new gen.Annotation(2000, "ss").setHost(endpoint1))
      Util.getClientAndServiceName(l) must be_==(Some("service", "service1"))
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
        List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava, "service", "service").setParent_id(0)
      val span1 = new gen.SpanServiceName(12345, "methodcall", 667,
        List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
        List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava, "service", "service").setParent_id(1)

      Util.repeatSpan(span, 1, 666, 0) must beEqualTo(List((span, 666),(span1, 667)))
    }
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
