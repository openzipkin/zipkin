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

COPY . /code/

#####
# zipkin-builder - An image that caches build repositories (.m2, .npm) to speed up subsequent builds.
#####

FROM openzipkin/zipkin-builder as built

COPY --from=scratch /code /code

WORKDIR /code

RUN mvn -B --no-transfer-progress package -DskipTests=true -pl zipkin-server -am

RUN mkdir -p /zipkin && cp zipkin-server/target/zipkin-server-*-exec.jar /zipkin && cd /zipkin && jar xf *.jar && rm *.jar
RUN mkdir -p /zipkin-slim && cp zipkin-server/target/zipkin-server-*-slim.jar /zipkin-slim && cd /zipkin-slim && jar xf *.jar && rm *.jar

FROM maven:3-jdk-11-slim as zipkin-builder

COPY --from=built /root/.m2 /root/.m2
COPY --from=built /root/.npm /root/.npm

#####
# zipkin-ui - An image containing the Zipkin web frontend only, served by NGINX
#####

FROM nginx:1.17-alpine as zipkin-ui
LABEL MAINTAINER Zipkin "https://zipkin.io/"

ENV ZIPKIN_BASE_URL=http://zipkin:9411

COPY --from=built /code/zipkin-lens/target/classes/zipkin-lens /var/www/html/zipkin
RUN mkdir -p /var/tmp/nginx && chown -R nginx:nginx /var/tmp/nginx

# Setup services
COPY docker/lens/nginx.conf /etc/nginx/conf.d/zipkin.conf.template
COPY docker/lens/run.sh /usr/local/bin/nginx.sh

EXPOSE 80

CMD ["/usr/local/bin/nginx.sh"]

#####
# zipkin-slim - An image containing the slim distribution of Zipkin server.
#####

FROM openzipkin/jre-full:14.0.1-14.28.21 as zipkin-slim
LABEL MAINTAINER Zipkin "https://zipkin.io/"

# Use to set heap, trust store or other system properties.
ENV JAVA_OPTS -Djava.security.egd=file:/dev/./urandom

RUN adduser -g '' -h /zipkin -D zipkin && ln -s /busybox/* /bin

COPY --from=built --chown=zipkin /zipkin-slim/ /zipkin/
# Add environment settings for supported storage types
COPY --chown=zipkin docker/zipkin/ /zipkin/
WORKDIR /zipkin

USER zipkin

EXPOSE 9411

# This health check was added for Docker Hub automated test service. Parameters
# were changed in order to mark success faster. You may want to change these
# further in production.
#
#
# By default, the Docker health check runs after 30s, and if a failure occurs,
# it waits 30s to try again. This implies a minimum of 30s before the server is
# marked healthy.
#
# https://docs.docker.com/engine/reference/builder/#healthcheck
#
# We expect the server startup to take less than 10 seconds, even in a fresh
# start. Some health checks will trigger a slow "first request" due to schema
# setup (ex this is the case in Elasticsearch and Cassandra). However, we don't
# want to force an initial delay of 30s as defaults would.
#
# Instead, we lower the interval and timeout from 30s to 5s. If a server starts
# in 7s and takes another 2s to install schema, it can still pass in 10s vs 30s.
#
# We retain the 30s even if it would be an excessively long startup. This is to
# accomodate test containers, which can boot slower than production sites, and
# any knock-on effects of that, like slow dependent storage containers which are
# simultaneously bootstrapping. If in production, you have a 30s startup, please
# report to https://gitter.im/openzipkin/zipkin including the values of the
# /health and /info endpoints as this would be unexpected.
#
HEALTHCHECK --interval=5s --start-period=30s --timeout=5s CMD wget -qO- http://localhost:9411/health &> /dev/null || exit 1

ENTRYPOINT ["/busybox/sh", "run.sh"]

#####
# zipkin-server - An image containing the full distribution of Zipkin server.
#####

FROM openzipkin/jre-full:14.0.1-14.28.21 as zipkin-server
LABEL MAINTAINER Zipkin "https://zipkin.io/"

# Use to set heap, trust store or other system properties.
ENV JAVA_OPTS -Djava.security.egd=file:/dev/./urandom
# 3rd party modules like zipkin-aws will apply profile settings with this
ENV MODULE_OPTS=

RUN adduser -g '' -h /zipkin -D zipkin && ln -s /busybox/* /bin

COPY --from=built --chown=zipkin /zipkin/ /zipkin/
# Add environment settings for supported storage types
COPY --chown=zipkin docker/zipkin/ /zipkin/
WORKDIR /zipkin

USER zipkin

EXPOSE 9410 9411

# This health check was added for Docker Hub automated test service. Parameters
# were changed in order to mark success faster. You may want to change these
# further in production.
#
#
# By default, the Docker health check runs after 30s, and if a failure occurs,
# it waits 30s to try again. This implies a minimum of 30s before the server is
# marked healthy.
#
# https://docs.docker.com/engine/reference/builder/#healthcheck
#
# We expect the server startup to take less than 10 seconds, even in a fresh
# start. Some health checks will trigger a slow "first request" due to schema
# setup (ex this is the case in Elasticsearch and Cassandra). However, we don't
# want to force an initial delay of 30s as defaults would.
#
# Instead, we lower the interval and timeout from 30s to 5s. If a server starts
# in 7s and takes another 2s to install schema, it can still pass in 10s vs 30s.
#
# We retain the 30s even if it would be an excessively long startup. This is to
# accomodate test containers, which can boot slower than production sites, and
# any knock-on effects of that, like slow dependent storage containers which are
# simultaneously bootstrapping. If in production, you have a 30s startup, please
# report to https://gitter.im/openzipkin/zipkin including the values of the
# /health and /info endpoints as this would be unexpected.
#
HEALTHCHECK --interval=5s --start-period=30s --timeout=5s CMD wget -qO- http://localhost:9411/health &> /dev/null || exit 1

ENTRYPOINT ["/busybox/sh", "run.sh"]
