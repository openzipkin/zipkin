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

# java_version is used for install and runtime layers of zipkin-elasticsearch6
#
# Use latest version here: https://github.com/orgs/openzipkin/packages/container/package/java
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG java_version=15.0.1_p9

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY build-bin/docker/docker-healthcheck /docker-bin/
COPY docker/test-images/zipkin-elasticsearch7/start-elasticsearch /docker-bin/
COPY docker/test-images/zipkin-elasticsearch6/config/ /config/

FROM ghcr.io/openzipkin/java:${java_version} as install

WORKDIR /install

# Use latest 6.x version from https://www.elastic.co/downloads/past-releases#elasticsearch
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG elasticsearch6_version=6.8.13

# Download only the OSS distribution (lacks X-Pack)
RUN \
# Connection resets are frequent in GitHub Actions workflows
wget --random-wait --tries=5 -qO- \
# We don't download bin scripts as we customize for reasons including BusyBox problems
https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-oss-$elasticsearch6_version.tar.gz| tar xz \
    --wildcards --strip=1 --exclude=*/bin && mkdir classes

COPY --from=scratch /config/ ./config/

FROM ghcr.io/openzipkin/java:${java_version}-jre as zipkin-elasticsearch6
LABEL org.opencontainers.image.description="Elasticsearch OSS distribution on OpenJDK and Alpine Linux"
ARG elasticsearch6_version=6.8.13
LABEL elasticsearch-version=$elasticsearch6_version

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENTRYPOINT ["start-elasticsearch"]

# All content including binaries and logs write under WORKDIR
ARG USER=elasticsearch
WORKDIR /${USER}

# Ensure the process doesn't run as root
RUN adduser -g '' -h ${PWD} -D ${USER}
USER ${USER}

# Copy binaries and config we installed earlier
COPY --from=install --chown=${USER} /install .

# Use to set heap, trust store or other system properties.
ENV JAVA_OPTS="-Xms256m -Xmx256m -XX:+ExitOnOutOfMemoryError"

EXPOSE 9200
