/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal.throttle

import com.netflix.concurrency.limits.Limiter
import com.netflix.concurrency.limits.Limiter.Listener
import com.netflix.concurrency.limits.limit.SettableLimit
import com.netflix.concurrency.limits.limiter.SimpleLimiter
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import zipkin2.Call
import zipkin2.Callback
import java.io.IOException
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ThrottledCallTest {
  val limit = SettableLimit.startingAt(0)
  val limiter = SimpleLimiter.newBuilder().limit(limit).build<Void>()

  val numThreads = 1
  val executor = Executors.newSingleThreadExecutor();
  @After fun shutdownExecutor() = executor.shutdown()

  inline fun <reified T : Any> mock() = Mockito.mock(T::class.java)

  @Test fun niceToString() {
    val delegate: Call<Void> = mock()
    `when`(delegate.toString()).thenReturn("StoreSpansCall{}")

    assertThat(ThrottledCall(executor, limiter, delegate))
      .hasToString("Throttled(StoreSpansCall{})")
  }

  @Test fun execute_isThrottled() {
    val queueSize = 1
    val totalTasks = numThreads + queueSize
    limit.limit = totalTasks

    val startLock = Semaphore(numThreads)
    val waitLock = Semaphore(totalTasks)
    val failLock = Semaphore(1)
    val throttled = throttle(LockedCall(startLock, waitLock))

    // Step 1: drain appropriate locks
    startLock.drainPermits()
    waitLock.drainPermits()
    failLock.drainPermits()

    // Step 2: saturate threads and fill queue
    val backgroundPool = Executors.newCachedThreadPool()
    for (i in 0 until totalTasks) {
      backgroundPool.submit(Callable { throttled.clone().execute() })
    }

    try {
      // Step 3: make sure the threads actually started
      startLock.acquire(numThreads)

      // Step 4: submit something beyond our limits
      val future = backgroundPool.submit(Callable {
        try {
          throttled.execute()
        } catch (e: IOException) {
          throw RuntimeException(e)
        } finally {
          // Step 6: signal that we tripped the limit
          failLock.release()
        }
      })

      // Step 5: wait to make sure our limit actually tripped
      failLock.acquire()

      future.get()

      // Step 7: Expect great things
      assertThat(true).isFalse() // should raise a RejectedExecutionException
    } catch (t: Throwable) {
      assertThat(t)
        .isInstanceOf(ExecutionException::class.java) // from future.get
        .hasCauseInstanceOf(RejectedExecutionException::class.java)
    } finally {
      waitLock.release(totalTasks)
      startLock.release(totalTasks)
      backgroundPool.shutdownNow()
    }
  }

  @Test fun execute_trottlesBack_whenStorageRejects() {
    val listener: Listener = mock()
    val call = FakeCall()
    call.overCapacity = true

    val throttle = ThrottledCall(executor, mockLimiter(listener), call)
    try {
      throttle.execute()
      assertThat(true).isFalse() // should raise a RejectedExecutionException
    } catch (e: RejectedExecutionException) {
      verify(listener).onDropped()
    }
  }

  @Test fun execute_ignoresLimit_whenPoolFull() {
    val listener: Listener = mock()

    val throttle = ThrottledCall(mockExhaustedPool(), mockLimiter(listener), FakeCall())
    try {
      throttle.execute()
      assertThat(true).isFalse() // should raise a RejectedExecutionException
    } catch (e: RejectedExecutionException) {
      verify(listener).onIgnore()
    }
  }

  @Test fun enqueue_isThrottled() {
    val queueSize = 1
    val totalTasks = numThreads + queueSize
    limit.limit = totalTasks

    val startLock = Semaphore(numThreads)
    val waitLock = Semaphore(totalTasks)
    val throttle = throttle(LockedCall(startLock, waitLock))

    // Step 1: drain appropriate locks
    startLock.drainPermits()
    waitLock.drainPermits()

    // Step 2: saturate threads and fill queue
    val callback: Callback<Void> = mock()
    for (i in 0 until totalTasks) {
      throttle.clone().enqueue(callback)
    }

    // Step 3: make sure the threads actually started
    startLock.acquire(numThreads)

    try {
      // Step 4: submit something beyond our limits and make sure it fails
      throttle.clone().enqueue(callback)

      assertThat(true).isFalse() // should raise a RejectedExecutionException
    } catch (e: RejectedExecutionException) {
    } finally {
      waitLock.release(totalTasks)
      startLock.release(totalTasks)
    }
  }

  @Test fun enqueue_throttlesBack_whenStorageRejects() {
    val listener: Listener = mock()
    val call = FakeCall()
    call.overCapacity = true

    val throttle = ThrottledCall(executor, mockLimiter(listener), call)
    val latch = CountDownLatch(1)
    throttle.enqueue(object : Callback<Void> {
      override fun onSuccess(value: Void) {
        latch.countDown()
      }

      override fun onError(t: Throwable) {
        latch.countDown()
      }
    })

    latch.await(1, TimeUnit.MINUTES)
    verify(listener).onDropped()
  }

  @Test fun enqueue_ignoresLimit_whenPoolFull() {
    val listener: Listener = mock()

    val throttle =
      ThrottledCall(mockExhaustedPool(), mockLimiter(listener), FakeCall())
    try {
      throttle.enqueue(null)
      assertThat(true).isFalse() // should raise a RejectedExecutionException
    } catch (e: RejectedExecutionException) {
      verify(listener).onIgnore()
    }
  }

  private fun throttle(delegate: Call<Void>) = ThrottledCall(executor, limiter, delegate)

  private class LockedCall(val startLock: Semaphore, val waitLock: Semaphore) : Call.Base<Void>() {
    override fun doExecute(): Void? {
      try {
        startLock.release()
        waitLock.acquire()
        return null;
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw AssertionError(e)
      }
    }

    override fun doEnqueue(callback: Callback<Void>) {
      try {
        callback.onSuccess(doExecute())
      } catch (t: Throwable) {
        callback.onError(t)
      }
    }

    override fun clone() = LockedCall(startLock, waitLock);
  }

  private fun mockExhaustedPool(): ExecutorService {
    val mock: ExecutorService = mock()
    doThrow(RejectedExecutionException::class.java).`when`(mock).execute(any())
    doThrow(RejectedExecutionException::class.java).`when`(mock).submit(any(Callable::class.java))
    return mock
  }

  private fun mockLimiter(listener: Listener): Limiter<Void> {
    val mock: Limiter<Void> = mock()
    `when`(mock.acquire(any())).thenReturn(Optional.of(listener))
    return mock
  }

  private class FakeCall(var overCapacity: Boolean = false) : Call.Base<Void>() {
    override fun doExecute(): Void? {
      if (overCapacity) throw RejectedExecutionException()
      return null
    }

    override fun doEnqueue(callback: Callback<Void>) {
      if (overCapacity) {
        callback.onError(RejectedExecutionException())
      } else {
        callback.onSuccess(null)
      }
    }

    override fun clone() = FakeCall()
  }
}
