/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.server.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Used to implement a context propagating executor service which wraps tasks */
// copy/pasted from Brave
public abstract class WrappingExecutorService implements ExecutorService {
  protected WrappingExecutorService() {
  }

  protected abstract ExecutorService delegate();

  protected abstract <C> Callable<C> wrap(Callable<C> task);

  protected abstract Runnable wrap(Runnable task);

  @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate().awaitTermination(timeout, unit);
  }

  @Override  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return delegate().invokeAll(wrap(tasks));
  }

  @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
      TimeUnit unit) throws InterruptedException {
    return delegate().invokeAll(wrap(tasks), timeout, unit);
  }

  @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return delegate().invokeAny(wrap(tasks));
  }

  @Override  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate().invokeAny(wrap(tasks), timeout, unit);
  }

  @Override public boolean isShutdown() {
    return delegate().isShutdown();
  }

  @Override public boolean isTerminated() {
    return delegate().isTerminated();
  }

  @Override public void shutdown() {
    delegate().shutdown();
  }

  @Override public List<Runnable> shutdownNow() {
    return delegate().shutdownNow();
  }

  @Override public void execute(Runnable task) {
    delegate().execute(wrap(task));
  }

  @Override public <T> Future<T> submit(Callable<T> task) {
    return delegate().submit(wrap(task));
  }

  @Override public Future<?> submit(Runnable task) {
    return delegate().submit(wrap(task));
  }

  @Override public <T> Future<T> submit(Runnable task, T result) {
    return delegate().submit(wrap(task), result);
  }

  <T> Collection<? extends Callable<T>> wrap(Collection<? extends Callable<T>> tasks) {
    ArrayList<Callable<T>> result = new ArrayList<>(tasks.size());
    for (Callable<T> task : tasks) {
      result.add(wrap(task));
    }
    return result;
  }
}
