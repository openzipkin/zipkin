package com.twitter.zipkin.json

import com.google.common.base.Charsets.UTF_8
import com.twitter.util.Time
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common._
import org.scalatest.{FunSuite, Matchers}
import java.nio.ByteBuffer

/**
 * Tests that who how data is serialized, so that subtle code changes don't break users.
 */
class ZipkinJsonTest extends FunSuite with Matchers {
  val mapper = ZipkinJson

  val web = Endpoint((192 << 24 | 168 << 16 | 1), 8080, "zipkin-web")
  val query = Endpoint((192 << 24 | 168 << 16 | 1), 9411, "zipkin-query")

  test("complete span example") {
    val s = Span(1, "get", 12345L, None, List(
      Annotation(1L, Constants.ClientSend, Some(web.copy(port = 0))),
      Annotation(2L, Constants.ServerRecv, Some(query)),
      Annotation(3L, Constants.ServerSend, Some(query)),
      Annotation(4L, Constants.ClientRecv, Some(web.copy(port = 0)))
    ), List(
      BinaryAnnotation("http.uri", ByteBuffer.wrap("/path".getBytes(UTF_8)), AnnotationType.String, Some(web.copy(port = 0))),
      BinaryAnnotation(Constants.ClientAddr, ByteBuffer.wrap(Array[Byte](1)), AnnotationType.Bool, Some(web)),
      BinaryAnnotation(Constants.ServerAddr, ByteBuffer.wrap(Array[Byte](1)), AnnotationType.Bool, Some(query))
    ), Some(true))
    assert(mapper.writeValueAsString(s) ==
      """
        |{
        |  "traceId": "0000000000000001",
        |  "name": "get",
        |  "id": "0000000000003039",
        |  "annotations": [
        |    {
        |      "timestamp": 1,
        |      "value": "cs",
        |      "endpoint": {
        |        "serviceName": "zipkin-web",
        |        "ipv4": "192.168.0.1"
        |      }
        |    },
        |    {
        |      "timestamp": 2,
        |      "value": "sr",
        |      "endpoint": {
        |        "serviceName": "zipkin-query",
        |        "ipv4": "192.168.0.1",
        |        "port": 9411
        |      }
        |    },
        |    {
        |      "timestamp": 3,
        |      "value": "ss",
        |      "endpoint": {
        |        "serviceName": "zipkin-query",
        |        "ipv4": "192.168.0.1",
        |        "port": 9411
        |      }
        |    },
        |    {
        |      "timestamp": 4,
        |      "value": "cr",
        |      "endpoint": {
        |        "serviceName": "zipkin-web",
        |        "ipv4": "192.168.0.1"
        |      }
        |    }
        |  ],
        |  "binaryAnnotations": [
        |    {
        |      "key": "http.uri",
        |      "value": "/path",
        |      "endpoint": {
        |        "serviceName": "zipkin-web",
        |        "ipv4": "192.168.0.1"
        |      }
        |    },
        |    {
        |      "key": "ca",
        |      "value": true,
        |      "endpoint": {
        |        "serviceName": "zipkin-web",
        |        "ipv4": "192.168.0.1",
        |        "port": 8080
        |      }
        |    },
        |    {
        |      "key": "sa",
        |      "value": true,
        |      "endpoint": {
        |        "serviceName": "zipkin-query",
        |        "ipv4": "192.168.0.1",
        |        "port": 9411
        |      }
        |    }
        |  ],
        |  "debug": true
        |}
      """.stripMargin.replaceAll("\\s",""))
  }

  test("endpoint") {
    assert(mapper.writeValueAsString(web.copy(port = 0)) ==
      """
        |{"serviceName":"zipkin-web","ipv4":"192.168.0.1"}
      """.stripMargin.trim
    )
  }

  test("endpoint: port 0 skips") {
    assert(mapper.writeValueAsString(web.copy(port = 0)) ==
      """
        |{"serviceName":"zipkin-web","ipv4":"192.168.0.1"}
      """.stripMargin.trim
    )
  }

  test("doesn't serialize absent fields of span") { // like debug or parentId
    val s = Span(1L, "zipkin-query", 2L, None, List.empty[Annotation], List.empty[BinaryAnnotation])
    assert(mapper.writeValueAsString(s) ==
      """
        |{"traceId":"0000000000000001","name":"zipkin-query","id":"0000000000000002","annotations":[],"binaryAnnotations":[]}
      """.stripMargin.trim
    )
  }

  test("annotation serializes timestamp as a number") {
    // it is ok to keep microsecond epoch timestamp as a number, not only because other code does,
    // but also because of this.
    val largestJsonNumber = Math.pow(2, 53) // not really largest, but a limitation of some browsers
    val yearWeBlowUpOn = Time.fromMicroseconds(largestJsonNumber.toLong).format("yyyy").toInt
    assert(yearWeBlowUpOn === 2255)

    val a = Annotation(1111L, Constants.ClientRecv, Some(web))
    assert(mapper.writeValueAsString(a) ==
      """
        |{"timestamp":1111,"value":"cr","endpoint":{"serviceName":"zipkin-web","ipv4":"192.168.0.1","port":8080}}
      """.stripMargin.trim
    )
  }

  test("doesn't serialize absent endpoint of annotation") {
    val a = Annotation(1111L, Constants.ClientRecv, None)
    assert(mapper.writeValueAsString(a) ==
      """
        |{"timestamp":1111,"value":"cr"}
      """.stripMargin.trim
    )
  }

  /** String type can be inferred from the json value */
  test("binary annotation: String doesn't need type") {
    val a = BinaryAnnotation("http.uri", ByteBuffer.wrap("/path".getBytes(UTF_8)), AnnotationType.String, None)
    assert(mapper.writeValueAsString(a) ==
      """
        |{"key":"http.uri","value":"/path"}
      """.stripMargin.trim
    )
  }

  test("binary annotation: Bytes base encodes and adds type") {
    val a = BinaryAnnotation("someKey", ByteBuffer.wrap(Array[Byte](1, 2, 3, 4)), AnnotationType.Bytes, Some(web))
    assert(mapper.writeValueAsString(a) ==
      """
        |{"key":"someKey","value":"AQIDBA==","type":"BYTES","endpoint":{"serviceName":"zipkin-web","ipv4":"192.168.0.1","port":8080}}
      """.stripMargin.trim
    )
  }

  test("binary annotation: Bool doesn't need type") {
    val a = BinaryAnnotation("someKey", ByteBuffer.wrap(Array[Byte](1)), AnnotationType.Bool, None)
    assert(mapper.writeValueAsString(a) ==
      """
        |{"key":"someKey","value":true}
      """.stripMargin.trim
    )
    assert(mapper.writeValueAsString(a.copy(value = ByteBuffer.wrap(Array[Byte](0)))) ==
      """
        |{"key":"someKey","value":false}
      """.stripMargin.trim
    )
  }

  test("binary annotation: I16 adds type") {
    val a = BinaryAnnotation("someKey", ByteBuffer.wrap(Array[Byte](1, 1)), AnnotationType.I16, None)
    assert(mapper.writeValueAsString(a) ==
      """
        |{"key":"someKey","value":257,"type":"I16"}
      """.stripMargin.trim
    )
  }

  test("binary annotation: I64 adds type") {
    val a = BinaryAnnotation("someKey", ByteBuffer.allocate(8).putLong(0, 1234L), AnnotationType.I64, None)
    assert(mapper.writeValueAsString(a) ==
      """
        |{"key":"someKey","value":1234,"type":"I64"}
      """.stripMargin.trim
    )
  }
}
