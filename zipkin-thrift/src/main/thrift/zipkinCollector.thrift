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
namespace java com.twitter.zipkin.gen
namespace rb Zipkin

include "scribe.thrift"

exception AdjustableRateException {
  1: string msg
}

exception StoreAggregatesException {
  1: string msg
}

service ZipkinCollector extends scribe.scribe {

    /** Aggregates methods */
    void storeTopAnnotations(1: string service_name, 2: list<string> annotations) throws (1: StoreAggregatesException e);
    void storeTopKeyValueAnnotations(1: string service_name, 2: list<string> annotations) throws (1: StoreAggregatesException e);
    void storeDependencies(1: string service_name, 2: list<string> endpoints) throws (1: StoreAggregatesException e);
}
