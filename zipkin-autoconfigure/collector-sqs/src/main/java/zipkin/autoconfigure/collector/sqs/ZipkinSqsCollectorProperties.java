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
package zipkin.autoconfigure.collector.sqs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin.collector.sqs.AwsSqsCollector;

@ConfigurationProperties("zipkin.collector.sqs")
final public class ZipkinSqsCollectorProperties {
  String queueUrl;
  int waitTimeSeconds = 20;
  int parallelism = 1;

  public String getQueueUrl() {
    return queueUrl;
  }

  public int getWaitTimeSeconds() {
    return waitTimeSeconds;
  }

  public int getParallelism() {
    return parallelism;
  }

  public AwsSqsCollector.Builder toBuilder() {
    return AwsSqsCollector.builder()
        .queueUrl(queueUrl)
        .parallelism(parallelism)
        .waitTimeSeconds(waitTimeSeconds);
  }
}
