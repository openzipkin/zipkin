#
# Copyright 2015-2020 The OpenZipkin Authors
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

# Refreshing the builder image to remove stale dependencies just involves building zipkin-server on
# an empty image.

FROM maven:3-openjdk-14-slim

COPY . /code/

RUN cd /code && mvn -B --no-transfer-progress package -DskipTests=true -pl zipkin-server -am && \
    cd / && rm -rf /code
