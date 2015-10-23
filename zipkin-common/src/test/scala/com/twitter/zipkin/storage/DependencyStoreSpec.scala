package com.twitter.zipkin.storage

import com.twitter.util.Await.result
import com.twitter.util.{Duration, Time}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.{Dependencies, DependencyLink, _}
import org.junit.{Before, Test}
import org.scalatest.Matchers
import org.scalatest.junit.JUnitSuite
import java.util.concurrent.TimeUnit._

/**
 * Base test for {@link DependencyStore} implementations. Subtypes should create a
 * connection to a real backend, even if that backend is in-process.
 *
 * <p/> This is JUnit-based to allow overriding tests and use of annotations
 * such as {@link org.junit.Ignore} and {@link org.junit.ClassRule}.
 */
abstract class DependencyStoreSpec extends JUnitSuite with Matchers {

  /** Notably, the cassandra implementation has day granularity */
  val day = MICROSECONDS.convert(1, DAYS)
  val today = Time.now.floor(Duration.fromMicroseconds(day)).inMicroseconds

  val zipkinWeb = Endpoint(172 << 24 | 17 << 16 | 3, 8080, "zipkin-web")
  val zipkinQuery = Endpoint(172 << 24 | 17 << 16 | 2, 9411, "zipkin-query")
  val zipkinJdbc = Endpoint(172 << 24 | 17 << 16 | 2, 0, "zipkin-jdbc")

  val trace = List(
    Span(1L, "GET", 1L, None, List(
      Annotation(today, Constants.ServerRecv, Some(zipkinWeb)),
      Annotation(today + 350, Constants.ServerSend, Some(zipkinWeb)))),
    Span(1L, "GET", 2L, Some(1L), List(
      Annotation(today + 50, Constants.ClientSend, Some(zipkinWeb)),
      Annotation(today + 100, Constants.ServerRecv, Some(zipkinQuery.copy(port = 0))),
      Annotation(today + 250, Constants.ServerSend, Some(zipkinQuery.copy(port = 0))),
      Annotation(today + 300, Constants.ClientRecv, Some(zipkinWeb))), List(
      BinaryAnnotation(Constants.ClientAddr, BinaryAnnotationValue(true), Some(zipkinWeb)),
      BinaryAnnotation(Constants.ServerAddr, BinaryAnnotationValue(true), Some(zipkinQuery)))),
    Span(1L, "query", 3L, Some(2L), List(
      Annotation(today + 150, Constants.ClientSend, Some(zipkinQuery)),
      Annotation(today + 200, Constants.ClientRecv, Some(zipkinQuery))), List(
      BinaryAnnotation(Constants.ClientAddr, BinaryAnnotationValue(true), Some(zipkinQuery)),
      BinaryAnnotation(Constants.ServerAddr, BinaryAnnotationValue(true), Some(zipkinJdbc))))
  )

  val dep = new Dependencies(today, today + 1000, List(
    new DependencyLink("zipkin-web", "zipkin-query", 1),
    new DependencyLink("zipkin-query", "zipkin-jdbc", 1)
  ))

  /**
   * Should maintain state between multiple calls within a test. Usually
   * implemented as a lazy.
   */
  def store: DependencyStore

  /** Clears the span store between tests. */
  def clear

  @Before def before() = clear

  def processDependencies(spans: List[Span])

  /**
   * Normally, the root-span is where trace id == span id and parent id == null.
   * The default is to look back one day from today.
   */
  @Test def getDependencies() = {
    processDependencies(trace)

    result(store.getDependencies(None, None)) should be(dep.links)
  }

  /**
   * When all servers are instrumented, they all log a "sr" annotation, indicating the service.
   */
  @Test def getDependenciesAllInstrumented() = {
    val one = Endpoint(127 << 24 | 1, 9410, "TraceProducerOne")
    val two = Endpoint(127 << 24 | 2, 9410, "TraceProducerTwo")
    val three = Endpoint(127 << 24 | 3, 9410, "TraceProducerThree")

    val trace = List(
      Span(10L, "GET", 10L, None, List(
        Annotation(1445136539256150L, Constants.ServerRecv, Some(one)),
        Annotation(1445136540408729L, Constants.ServerSend, Some(one)))),
      Span(10L, "GET", 20L, Some(10L), List(
        Annotation(1445136539764798L, Constants.ClientSend, Some(one.copy(port = 3001))),
        Annotation(1445136539816432L, Constants.ServerRecv, Some(two)),
        Annotation(1445136540401414L, Constants.ServerSend, Some(two)),
        Annotation(1445136540404135L, Constants.ClientRecv, Some(one.copy(port = 3001))))),
      Span(10L, "query", 30L, Some(20L), List(
        Annotation(1445136540025751L, Constants.ClientSend, Some(two.copy(port = 3002))),
        Annotation(1445136540072846L, Constants.ServerRecv, Some(three)),
        Annotation(1445136540394644L, Constants.ServerSend, Some(three)),
        Annotation(1445136540397049L, Constants.ClientRecv, Some(two.copy(port = 3002)))))
    )
    processDependencies(trace)

    result(store.getDependencies(Some(trace(0).startTs.get), Some(trace(0).endTs.get))).sortBy(_.parent) should be(
      List(
        new DependencyLink("TraceProducerOne", "TraceProducerTwo", 1),
        new DependencyLink("TraceProducerTwo", "TraceProducerThree", 1)
      )
    )
  }

  /**
   * The primary annotation used in the dependency graph is [[Constants.ServerRecv]]
   */
  @Test def getDependenciesMultiLevel() = {
    processDependencies(trace)

    result(store.getDependencies(None, None)) should be(dep.links)
  }

  @Test def dependencies_loopback {
    val traceWithLoopback = List(
      trace(0),
      trace(1).copy(annotations = trace(1).annotations.map(a => a.copy(host = Some(zipkinWeb))),
                    binaryAnnotations = List.empty)
    )

    processDependencies(traceWithLoopback)

    result(store.getDependencies(None, None)) should be(Dependencies.toLinks(traceWithLoopback))
  }

  /**
   * Some systems log a different trace id than the root span. This seems "headless", as we won't
   * see a span whose id is the same as the trace id.
   */
  @Test def dependencies_headlessTrace {
    processDependencies(List(trace(1), trace(2)))

    result(store.getDependencies(None, None)) should be(Dependencies.toLinks(List(trace(1), trace(2))))
  }


  @Test def getDependencies_looksBackOneDay() = {
    processDependencies(trace)

    result(store.getDependencies(None, Some(today + day))) should be(dep.links)
  }

  @Test def getDependencies_insideTheInterval() = {
    processDependencies(trace)

    result(store.getDependencies(Some(dep.startTs), Some(dep.endTs))) should be(dep.links)
  }

  @Test def getDependencies_endTimeBeforeData() = {
    processDependencies(trace)

    result(store.getDependencies(None, Some(today - day))) should be(empty)
  }

  @Test def getDependencies_endTimeAfterData() = {
    processDependencies(trace)

    result(store.getDependencies(None, Some(today + 2 * day))) should be(empty)
  }
}
