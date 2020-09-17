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

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY zipkin-storage/cassandra/src/main/resources/*.cql /zipkin-schemas/
COPY zipkin-storage/cassandra-v1/src/main/resources/cassandra-schema.cql /zipkin-schemas/

# Until Cassandra supports recent JDKs, we need to install with the JDK 1.8
FROM openzipkin/jre-full:1.8.0_252 as install

# Use latest stable release here.
ENV CASSANDRA_VERSION=3.11.8

WORKDIR /install

COPY --from=scratch /zipkin-schemas/* ./zipkin-schemas/
COPY docker/storage/cassandra/install.sh /tmp/
RUN /tmp/install.sh && rm /tmp/install.sh

# Share the same base image to reduce layers used in testing
FROM openzipkin/jre-full:1.8.0_252
MAINTAINER OpenZipkin "https://zipkin.io/"

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY docker/storage/cassandra/docker-bin/* /usr/local/bin/

# All content including binaries and logs write under WORKDIR
ARG USER=cassandra
WORKDIR /${USER}

# Ensure the process doesn't run as root
RUN adduser -g '' -h ${PWD} -D ${USER}
USER ${USER}

# Copy binaries and config we installed earlier
COPY --from=install --chown=${USER} /install .

ENV JAVA=/usr/local/java/jre/bin/java \
    JVM_OPTS="-Dcassandra -Djava.net.preferIPv4Stack=true"

EXPOSE 9160 7000 7001 9042 7199

ENTRYPOINT ["start-cassandra"]

