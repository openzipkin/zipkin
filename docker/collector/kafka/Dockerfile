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

FROM alpine

ENV SCALA_VERSION 2.12
ENV KAFKA_VERSION 2.4.0
ENV ZOOKEEPER_VERSION 3.5.6

WORKDIR /kafka
ADD docker/collector/kafka/install.sh /kafka/install
RUN /kafka/install

ADD docker/collector/kafka/wait-for-zookeeper.sh /kafka/bin
ADD docker/collector/kafka/start.sh /kafka/bin

# Share the same base image to reduce layers used in testing
FROM openzipkin/jre-full:11.0.5-11.35
LABEL MAINTAINER Zipkin "https://zipkin.io/"

WORKDIR /kafka

RUN ["/busybox/sh", "-c", "adduser -g '' -h /kafka -D kafka"]

COPY --from=0 --chown=kafka /kafka /kafka

USER kafka

# Port 19092 is for connections from the Docker host
EXPOSE 2181 9092 19092

ENTRYPOINT ["/busybox/sh", "/kafka/bin/start.sh"]
