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

ENV ELASTICSEARCH_VERSION 6.8.11

RUN apk add --update curl

WORKDIR /elasticsearch

RUN curl -sSL https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-$ELASTICSEARCH_VERSION.tar.gz| tar xz && \
    mv elasticsearch-$ELASTICSEARCH_VERSION/* /elasticsearch/ && \
    # remove binaries and non shell scripts
    rm -f /elasticsearch/bin/elasticsearch-sql* /elasticsearch/bin/*cmd /elasticsearch/bin/*exe && \
    # Remove bash as our images doesn't have it, and it isn't required
    ls -d /elasticsearch/bin/* | grep -v x-pack | xargs sed -i 's~#!/bin/bash~#!/bin/sh~g' && \
    chmod +x /elasticsearch/bin/*

COPY docker/storage/elasticsearch6/config /elasticsearch/config

FROM openzipkin/jre-full:14.0.2-14.29.23
LABEL MAINTAINER Zipkin "https://zipkin.io/"

# https://github.com/elastic/elasticsearch/pull/31003 was closed won't fix
ENV ES_TMPDIR /tmp

RUN adduser -g '' -h /elasticsearch -D elasticsearch

COPY --from=0 --chown=elasticsearch /elasticsearch /elasticsearch

WORKDIR /elasticsearch

# elasticsearch complains if run as root
USER elasticsearch

EXPOSE 9200 9300

ENV ES_JAVA_OPTS " "

ENTRYPOINT /elasticsearch/bin/elasticsearch
