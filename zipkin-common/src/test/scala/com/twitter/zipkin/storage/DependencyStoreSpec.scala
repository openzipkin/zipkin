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
      Annotation(today + 350, Constants.ServerSend, Some(zipkinWeb))), Seq.empty),
    Span(1L, "GET", 2L, Some(1L), List(
      Annotation(today + 50, Constants.ServerRecv, Some(zipkinQuery)),
      Annotation(today + 100, Constants.ClientSend, Some(zipkinQuery.copy(port = 0))),
      Annotation(today + 250, Constants.ClientRecv, Some(zipkinQuery.copy(port = 0))),
      Annotation(today + 300, Constants.ServerSend, Some(zipkinQuery))), Seq.empty),
    Span(1L, "query", 3L, Some(2L), List(
      Annotation(today + 150, Constants.ClientSend, Some(zipkinJdbc)),
      Annotation(today + 200, Constants.ClientRecv, Some(zipkinJdbc))), Seq.empty)
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


  @Test def dependencies_loopback {
    val traceWithLoopback = List(
      trace(0),
      trace(1).copy(annotations = trace(1).annotations.map( a => a.copy(host = Some(zipkinWeb))))
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
