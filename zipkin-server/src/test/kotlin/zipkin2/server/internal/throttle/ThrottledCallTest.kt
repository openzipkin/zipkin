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
import java.util.function.Supplier

// TODO: this class re-uses Call objects which is bad as they are one-shot. This needs to be
//  refactored in order to be realistic (calls throw if re-invoked, as clone() is the correct way)
class ThrottledCallTest {
  var limit = SettableLimit.startingAt(0)
  var limiter = SimpleLimiter.newBuilder().limit(limit).build<Void>()

  inline fun <reified T : Any> mock() = Mockito.mock(T::class.java)

  @Test fun callCreation_isDeferred() {
    val created = booleanArrayOf(false)

    val throttle = createThrottle(Supplier {
      created[0] = true
      Call.create<Void>(null)
    })

    assertThat(created).contains(false)
    throttle.execute()
    assertThat(created).contains(true)
  }

  @Test fun execute_isThrottled() {
    val numThreads = 1
    val queueSize = 1
    val totalTasks = numThreads + queueSize

    val startLock = Semaphore(numThreads)
    val waitLock = Semaphore(totalTasks)
    val failLock = Semaphore(1)
    val throttle =
      createThrottle(numThreads, queueSize, Supplier { LockedCall(startLock, waitLock) })

    // Step 1: drain appropriate locks
    startLock.drainPermits()
    waitLock.drainPermits()
    failLock.drainPermits()

    // Step 2: saturate threads and fill queue
    val backgroundPool = Executors.newCachedThreadPool()
    for (i in 0 until totalTasks) {
      backgroundPool.submit(Callable { throttle.execute() })
    }

    try {
      // Step 3: make sure the threads actually started
      startLock.acquire(numThreads)

      // Step 4: submit something beyond our limits
      val future = backgroundPool.submit(Callable {
        try {
          throttle.execute()
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

    val throttle = ThrottledCall(createPool(1, 1), mockLimiter(listener), Supplier { call })
    try {
      throttle.execute()
      assertThat(true).isFalse() // should raise a RejectedExecutionException
    } catch (e: RejectedExecutionException) {
      verify(listener).onDropped()
    }
  }

  @Test fun execute_ignoresLimit_whenPoolFull() {
    val listener: Listener = mock()

    val throttle =
      ThrottledCall(mockExhaustedPool(), mockLimiter(listener), Supplier { FakeCall() })
    try {
      throttle.execute()
      assertThat(true).isFalse() // should raise a RejectedExecutionException
    } catch (e: RejectedExecutionException) {
      verify(listener).onIgnore()
    }
  }

  @Test fun enqueue_isThrottled() {
    val numThreads = 1
    val queueSize = 1
    val totalTasks = numThreads + queueSize

    val startLock = Semaphore(numThreads)
    val waitLock = Semaphore(totalTasks)
    val throttle =
      createThrottle(numThreads, queueSize, Supplier { LockedCall(startLock, waitLock) })

    // Step 1: drain appropriate locks
    startLock.drainPermits()
    waitLock.drainPermits()

    // Step 2: saturate threads and fill queue
    val callback: Callback<Void> = mock()
    for (i in 0 until totalTasks) {
      throttle.enqueue(callback)
    }

    // Step 3: make sure the threads actually started
    startLock.acquire(numThreads)

    try {
      // Step 4: submit something beyond our limits and make sure it fails
      throttle.enqueue(callback)

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

    val throttle = ThrottledCall(createPool(1, 1), mockLimiter(listener), Supplier { call })
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
      ThrottledCall(mockExhaustedPool(), mockLimiter(listener), Supplier { FakeCall() })
    try {
      throttle.enqueue(null)
      assertThat(true).isFalse() // should raise a RejectedExecutionException
    } catch (e: RejectedExecutionException) {
      verify(listener).onIgnore()
    }
  }

  private fun createThrottle(delegate: Supplier<Call<Void>>): ThrottledCall<Void> {
    return createThrottle(1, 1, delegate)
  }

  private fun createThrottle(
    poolSize: Int,
    queueSize: Int,
    delegate: Supplier<Call<Void>>
  ): ThrottledCall<Void> {
    limit.setLimit(limit.getLimit() + 1)
    return ThrottledCall(createPool(poolSize, queueSize), limiter, delegate)
  }

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

  private fun createPool(poolSize: Int, queueSize: Int): ExecutorService {
    return ThreadPoolExecutor(poolSize, poolSize, 0, TimeUnit.DAYS,
      LinkedBlockingQueue(queueSize))
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
