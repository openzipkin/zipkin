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

# java_version is used for install and runtime layers of zipkin-cassandra
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
COPY docker/test-images/zipkin-cassandra/start-cassandra /docker-bin/
COPY docker/test-images/zipkin-cassandra/install.sh /install/
COPY zipkin-storage/cassandra/src/main/resources/*.cql /zipkin-schemas/

FROM ghcr.io/openzipkin/java:${java_version} as install

# Use latest stable version: https://cassandra.apache.org/download/
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG cassandra_version=3.11.9
ENV CASSANDRA_VERSION=$cassandra_version
WORKDIR /install

COPY --from=scratch /zipkin-schemas/* ./zipkin-schemas/
COPY --from=scratch /install/install.sh /tmp/
RUN /tmp/install.sh && rm /tmp/install.sh

FROM ghcr.io/openzipkin/java:${java_version}-jre as zipkin-cassandra
LABEL org.opencontainers.image.description="Cassandra on OpenJDK and Alpine Linux with Zipkin keyspaces pre-installed"
ARG cassandra_version=3.11.9
LABEL cassandra-version=$cassandra_version
ENV CASSANDRA_VERSION=$cassandra_version

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENTRYPOINT ["start-cassandra"]

# All content including binaries and logs write under WORKDIR
ARG USER=cassandra
WORKDIR /${USER}

# Ensure the process doesn't run as root
RUN adduser -g '' -h ${PWD} -D ${USER}
USER ${USER}

# Copy binaries and config we installed earlier
COPY --from=install --chown=${USER} /install .

# Set variables Cassandra's start script wants
ENV JAVA_OPTS="-Xms256m -Xmx256m -XX:+ExitOnOutOfMemoryError -Djava.net.preferIPv4Stack=true"
ENV LOGGING_LEVEL=WARN

EXPOSE 9042
