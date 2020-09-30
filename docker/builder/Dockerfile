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
LABEL MAINTAINER Zipkin "https://zipkin.io/"

COPY . /code/

# Unset MAVEN_CONFIG until we don't depend on the maven image which conflicts with the Takari env
ENV MAVEN_CONFIG=

# The image retains /root/.m2 and /root/.npm to optimize for DockerHub which doesn't support
# another way to retain cache between build runs.
#
# The below commands are equivalent to executing Maven go-offline and NPM commands, but take longer
# due to compilation. We use this as it is the same command mentioned in zipkin-server/README.md
RUN cd /code && \
    ./mvnw -q --batch-mode -DskipTests --also-make -pl zipkin-server package && \
    cd / && rm -rf /code
