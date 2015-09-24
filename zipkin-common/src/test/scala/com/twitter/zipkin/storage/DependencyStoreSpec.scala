package com.twitter.zipkin.storage

import com.twitter.conversions.time._
import com.twitter.util.{Await, Time}
import com.twitter.zipkin.common.{Dependencies, DependencyLink}
import org.junit.{Before, Test}
import org.scalatest.Matchers
import org.scalatest.junit.JUnitSuite
/**
 * Base test for {@link DependencyStore} implementations. Subtypes should create a
 * connection to a real backend, even if that backend is in-process.
 *
 * <p/> This is JUnit-based to allow overriding tests and use of annotations
 * such as {@link org.junit.Ignore} and {@link org.junit.ClassRule}.
 */
abstract class DependencyStoreSpec extends JUnitSuite with Matchers {
  /**
   * Should maintain state between multiple calls within a test. Usually
   * implemented as a lazy.
   */
  def store: DependencyStore

  /** Clears the span store between tests. */
  def clear

  @Before def before() = clear

  @Test def storeAndGetDependencies() {
    val dl1 = new DependencyLink("parent1", "child1", 18)
    val dl2 = new DependencyLink("parent2", "child2", 42)
    val dep1 = new Dependencies(Time.now.minus(7.days), Time.now, List(dl1, dl2))

    Await.result(store.storeDependencies(dep1))

    val agg1 = Await.result(store.getDependencies(Some(dep1.startTime), Some(dep1.endTime))) // Inclusive, start to end
    val agg2 = Await.result(store.getDependencies(Some(Time.fromSeconds(0)), Some(Time.now))) // All time
    val agg3 = Await.result(store.getDependencies(Some(Time.fromSeconds(0)), None)) // 0 to +1.day

    val agg4 = Await.result(store.getDependencies(Some(Time.fromSeconds(0)), Some(Time.fromSeconds(1) + 1.millisecond))) // end inside the dependency
    val agg5 = Await.result(store.getDependencies(Some(Time.fromSeconds(1) + 1.millisecond), Some(Time.fromSeconds(2) - 1.millisecond))) // start and end inside the dependency
    val agg6 = Await.result(store.getDependencies(Some(Time.fromSeconds(1) + 1.millisecond), Some(Time.fromSeconds(3)))) // start inside the dependency

    assert(agg1.links === dep1.links)
    assert(agg2.links === dep1.links)
    assert(agg3.links === dep1.links)

    assert(agg4.links.isEmpty)
    assert(agg5.links.isEmpty)
    assert(agg6.links.isEmpty)
  }
}
