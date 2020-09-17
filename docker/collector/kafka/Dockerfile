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

FROM openzipkin/zipkin-builder as install

# Use latest stable release here. Scala 2.13+ supports JRE 14
ENV KAFKA_VERSION=2.6.0 SCALA_VERSION=2.13

WORKDIR /install
COPY docker/collector/kafka/install.sh /tmp/
RUN /tmp/install.sh && rm /tmp/install.sh

# Share the same base image to reduce layers used in testing
FROM openzipkin/jre-full:14.0.2-14.29.23
LABEL MAINTAINER Zipkin "https://zipkin.io/"

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY docker/collector/kafka/docker-bin/* /usr/local/bin/

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
EXPOSE 2181 9092 19092

# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]

ENTRYPOINT ["start-kafka-zookeeper"]
