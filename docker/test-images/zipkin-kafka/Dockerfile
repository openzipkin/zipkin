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

# java_version is used for install and runtime layers of zipkin-kafka
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
COPY docker/test-images/zipkin-kafka/start-kafka-zookeeper /docker-bin/
COPY docker/test-images/zipkin-kafka/install.sh /install/

FROM ghcr.io/openzipkin/java:${java_version} as install

# Use latest release from: https://kafka.apache.org/downloads
#
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG kafka_version=2.7.0
ENV KAFKA_VERSION=$kafka_version
# Note: Scala 2.13+ supports JRE 14
ARG scala_version=2.13
ENV SCALA_VERSION=$scala_version

WORKDIR /install
COPY --from=scratch /install/install.sh /tmp/
RUN /tmp/install.sh && rm /tmp/install.sh

# Share the same base image to reduce layers used in testing
FROM ghcr.io/openzipkin/java:${java_version}-jre as zipkin-kafka
LABEL org.opencontainers.image.description="Kafka and ZooKeeper on OpenJDK and Alpine Linux"
ARG kafka_version=2.7.0
LABEL kafka-version=$kafka_version

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENTRYPOINT ["start-kafka-zookeeper"]

# All content including binaries and logs write under WORKDIR
ARG USER=kafka
WORKDIR /${USER}

# Ensure the process doesn't run as root
RUN adduser -g '' -h ${PWD} -D ${USER}
USER ${USER}

# Copy binaries and config we installed earlier
COPY --from=install --chown=${USER} /install .

# ${KAFKA_ADVERTISED_HOST_NAME}:19092 is for connections from the Docker host
ENV KAFKA_ADVERTISED_HOST_NAME=localhost
# Use to set heap, trust store or other system properties.
ENV ZOOKEEPER_JAVA_OPTS="-Xms32m -Xmx32m -XX:+ExitOnOutOfMemoryError"
ENV JAVA_OPTS="-Xms256m -Xmx256m -XX:+ExitOnOutOfMemoryError"
ENV LOGGING_LEVEL=WARN

EXPOSE 2181 9092 19092
