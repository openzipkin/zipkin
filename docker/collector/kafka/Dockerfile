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

FROM alpine:3.12

ENV SCALA_VERSION 2.13
ENV KAFKA_VERSION 2.6.0

WORKDIR /kafka
ADD docker/collector/kafka/install.sh /kafka/install
RUN /kafka/install && rm /kafka/install

ADD docker/collector/kafka/wait-for-zookeeper.sh /kafka/bin
ADD docker/collector/kafka/start.sh /kafka/bin

# Share the same base image to reduce layers used in testing
FROM openzipkin/jre-full:14.0.2-14.29.23
LABEL MAINTAINER Zipkin "https://zipkin.io/"

WORKDIR /kafka

RUN adduser -g '' -h /kafka -D kafka

COPY --from=0 --chown=kafka /kafka /kafka

USER kafka

# Port 19092 is for connections from the Docker host
EXPOSE 2181 9092 19092

# We use start period of 10s to avoid marking Kafka unhealthy on slow or contended CI hosts
#
# We use a timeout of 5s as kafka-topics.sh invokes a Java command which can be slow when
# multiple processes are starting up (ex docker-compose with >10 containers). A smaller timeout
# flips the service unhealthy and can interfere with docker-compose v2 condition: service_healthy
HEALTHCHECK --interval=2s --start-period=10s --timeout=5s \
  # We use bootstrap-server, not zookeeper, as KIP-500 will eventually remove the dependency
  CMD /kafka/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --list > /dev/null || exit 1

ENTRYPOINT /kafka/bin/start.sh
