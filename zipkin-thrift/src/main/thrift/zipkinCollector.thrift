# Copyright 2012 Twitter Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
namespace java com.twitter.zipkin.gen.collector

include "scribe.thrift"

exception AdjustableRateException {
  1: string msg
}

service ZipkinCollector {

    // Implement the Scribe interface
    scribe.ResultCode Log(1: list<scribe.LogEntry> messages);

    //************** ZK config changes **************

    /**
     * Get the sample rate in ZooKeeper for the cluster
     */
    double getSampleRate() throws (1: AdjustableRateException qe);

    /**
     * Set sample rate in ZooKeeper for the cluster
     */
    void setSampleRate(1: double sample_rate) throws (1: AdjustableRateException qe);

    /**
     * Get the storage request rate in ZooKeeper for the cluster
     * The storage request rate is used as the target number of storage requests
     * per minute
     */
    double getStorageRequestRate() throws (1: AdjustableRateException e);

    /**
     * Set the storage request rate in ZooKeeper for the cluster
     */
    void setStorageRequestRate(1: double storage_request_rate) throws (1: AdjustableRateException e);
}
