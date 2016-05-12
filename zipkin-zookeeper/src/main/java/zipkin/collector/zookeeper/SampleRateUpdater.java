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

import com.google.common.collect.ImmutableMap;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.nodes.GroupMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static zipkin.internal.Util.UTF_8;

/**
 * This watches the store rate path for updates.
 */
final class SampleRateUpdater implements PathChildrenCacheListener, Closeable {
  final Logger log = LoggerFactory.getLogger(SampleRateUpdater.class);

  private final GroupMember storeRateMember;
  private final String sampleRatePath;
  private final Function<Map<String, Integer>, Optional<Float>> calculator;
  private final Supplier<Boolean> guard;
  private final PathChildrenCache dataWatcher;

  SampleRateUpdater(CuratorFramework client,
      GroupMember storeRateMember,
      String storeRatePath,
      String sampleRatePath,
      Function<Map<String, Integer>, Optional<Float>> calculator,
      Supplier<Boolean> guard
  ) {
    this.storeRateMember = storeRateMember;
    this.sampleRatePath = sampleRatePath;
    this.calculator = calculator;
    this.guard = guard;
    // We don't need to cache the data as we can already access it from storeRateMember
    this.dataWatcher = new PathChildrenCache(client, storeRatePath, false);
    try {
      this.dataWatcher.start();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    dataWatcher.getListenable().addListener(this);
  }

  @Override public void close() throws IOException {
    dataWatcher.close();
  }

  @Override public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) {
    switch (event.getType()) {
      case CHILD_ADDED:
      case CHILD_UPDATED:
      case CHILD_REMOVED:
        break;
      default:
        return;
    }
    ImmutableMap.Builder<String, Integer> builder = new ImmutableMap.Builder();
    for (Map.Entry<String, byte[]> i : storeRateMember.getCurrentMembers().entrySet()) {
      if (i.getValue() == null) continue;
      try {
        builder.put(i.getKey(), Integer.valueOf(new String(i.getValue(), UTF_8)));
      } catch (NumberFormatException e) {
        log.debug("malformed data at path {}: {}", i.getKey(), e.getMessage());
      }
    }
    Optional<Float> newSampleRate = calculator.apply(builder.build());
    if (!newSampleRate.isPresent() || !guard.get()) return;
    Float rate = newSampleRate.get();
    log.info("updating sample rate: {} {}", sampleRatePath, rate);
    try {
      client.create().creatingParentsIfNeeded()
          .forPath(sampleRatePath, rate.toString().getBytes(UTF_8));
    } catch (Exception e) {
      log.warn("could not set sample rate to {} for {}", rate, sampleRatePath);
    }
  }
}
