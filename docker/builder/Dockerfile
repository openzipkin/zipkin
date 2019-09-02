#
# Copyright 2015-2019 The OpenZipkin Authors
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

FROM maven:3-jdk-11-slim

COPY . /code/

# We run a normal build, sans zipkin-lens, which causes Maven to download most dependencies (it
# misses out on error prone since we only copy pom files into the build context and no Java files,
# so error prone is skipped). Then we run the generate-resources goal on zipkin-lens, which sets up
# node and npm dependencies. The result is a cache of most build-time dependencies.

RUN cd /code && \
    mvn -B package -Dlicense.skip=true -DskipTests=true -DskipLens=true && \
    mvn -B generate-resources -pl zipkin-lens -Dlicense.skip=true -DskipTests=true && \
    cd / && rm -rf /code
