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

# TODO: move to alpine when supported https://github.com/openzipkin/zipkin/issues/3218
FROM azul/zulu-openjdk-alpine:15.0.0-15.27.17 as install
LABEL MAINTAINER Zipkin "https://zipkin.io/"

WORKDIR /install

# Use the same command as we suggest in zipkin-server/README.md to hydrate the Maven and NPM cache
COPY . /install/.
RUN ./mvnw -q --batch-mode -DskipTests --also-make -pl zipkin-server package

FROM azul/zulu-openjdk-alpine:15.0.0-15.27.17
MAINTAINER OpenZipkin "https://zipkin.io/"

# BusyBux built-in tar doesn't support --strip=1
RUN apk add --update --no-cache tar

# Use latest stable release here.
ARG MAVEN_VERSION=3.6.3
WORKDIR /usr/local/maven
RUN APACHE_MIRROR=$(wget -qO- https://www.apache.org/dyn/closer.cgi\?as_json\=1 | sed -n '/preferred/s/.*"\(.*\)"/\1/gp') && \
    wget -qO- $APACHE_MIRROR/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xz --strip=1 && \
    ln -s /usr/local/maven/bin/mvn /usr/local/bin/mvn

WORKDIR /

# Copy Maven cache hydrated earlier
COPY --from=install /root/.m2 /root/.m2
COPY --from=install /root/.npm /root/.npm
