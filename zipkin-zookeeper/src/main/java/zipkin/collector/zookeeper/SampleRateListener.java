/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.collector.zookeeper;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.internal.Util;

/** Listens for changes in sample rate and updates boundary accordingly */
final class SampleRateListener implements NodeCacheListener, Closeable {
  final Logger log = LoggerFactory.getLogger(SampleRateListener.class);

  final AtomicReference<Float> sampleRate;
  final AtomicLong boundary;
  final NodeCache cache;

  SampleRateListener(CuratorFramework client, String sampleRatePath,
      AtomicReference<Float> sampleRate, AtomicLong boundary) {
    this.sampleRate = sampleRate;
    this.boundary = boundary;
    try {
      client.checkExists().creatingParentContainersIfNeeded().forPath(sampleRatePath);
    } catch (Exception e) {
      throw new IllegalStateException("Error creating " + sampleRatePath, e);
    }
    this.cache = new NodeCache(client, sampleRatePath);
    try {
      this.cache.start();
    } catch (Exception e) {
      throw new IllegalStateException("Error starting cache for " + sampleRatePath, e);
    }
    this.cache.getListenable().addListener(this);
  }

  @Override public void nodeChanged() throws Exception {
    byte[] bytes = cache.getCurrentData().getData();
    if (bytes.length == 0) return;
    Float newSampleRate;
    try {
      newSampleRate = Float.valueOf(new String(bytes, Util.UTF_8));
    } catch (NumberFormatException e) {
      log.warn("Error parsing sampleRate {}", e.getMessage());
      return;
    }
    if (newSampleRate < 0.0f || newSampleRate > 1.0f) {
      log.warn("sampleRate should be between 0 and 1: was {}", newSampleRate);
    } else {
      sampleRate.set(newSampleRate);
      boundary.set((long) (Long.MAX_VALUE * newSampleRate));
    }
  }

  @Override public void close() throws IOException {
    cache.close();
  }
}
