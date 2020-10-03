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

FROM azul/zulu-openjdk-alpine:15.0.0-15.27.17 as built
LABEL MAINTAINER Zipkin "https://zipkin.io/"

# Install Maven, tar and libc hooks
COPY docker/builder/install.sh /tmp/
RUN /tmp/install.sh && rm /tmp/install.sh

WORKDIR /code

# Copy Maven source tree into the Docker context
COPY . .

# Make sure zipkin-lens can build on Alpine by eagerly caching an unofficial build.
# Temporarily workaround https://github.com/eirslett/frontend-maven-plugin/pull/853
COPY docker/builder/download-node.sh .
RUN ./download-node.sh

# Use the same command as we suggest in zipkin-server/README.md to hydrate the Maven and NPM cache
#  * Uses mvn not ./mvnw to reduce layer size: we control the Maven version independently in Docker
RUN mvn -q --batch-mode -DskipTests -Dlicense.skip=true --also-make -pl zipkin-server package && \
    # Delete /code to retain only ~/.m2 and ~/.npm
    cd / && rm -rf /code

WORKDIR /
