package com.twitter.zipkin.storage

import com.twitter.util.Await.{ready, result}
import com.twitter.util.{Duration, Time}
import com.twitter.zipkin.common.{Dependencies, DependencyLink}
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
  val dep = new Dependencies(today, today + 1000, List(
    new DependencyLink("zipkin-web", "zipkin-query", 18),
    new DependencyLink("zipkin-query", "cassandra", 42)
  ))

  /**
   * Should maintain state between multiple calls within a test. Usually
   * implemented as a lazy.
   */
  def store: DependencyStore

  /** Clears the span store between tests. */
  def clear

  @Before def before() = clear

  @Test def getDependencies_defaultsToToday() = {
    ready(store.storeDependencies(dep))

    result(store.getDependencies(None, None)) should be(dep)
  }

  @Test def getDependencies_looksBackOneDay() = {
    ready(store.storeDependencies(dep))

    result(store.getDependencies(None, Some(today + day))) should be(dep)
  }

  @Test def getDependencies_insideTheInterval() = {
    ready(store.storeDependencies(dep))

    result(store.getDependencies(Some(dep.startTime), Some(dep.endTime))) should be(dep)
  }

  @Test def getDependencies_endTimeBeforeData() = {
    ready(store.storeDependencies(dep))

    result(store.getDependencies(None, Some(today - day))) should be(Dependencies.zero)
  }

  @Test def getDependencies_endTimeAfterData() = {
    ready(store.storeDependencies(dep))

    result(store.getDependencies(None, Some(today + 2 * day))) should be(Dependencies.zero)
  }
}
