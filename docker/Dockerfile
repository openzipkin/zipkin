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

ARG java_version=15.0.0-15.27.17

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY . /code/

#####
# zipkin-builder - An image that caches build repositories (.m2, .npm) to speed up subsequent builds.
#####

FROM openzipkin/zipkin-builder as built

WORKDIR /code
COPY --from=scratch /code .

# Use the same command as we suggest in zipkin-server/README.md
#  * Uses mvn not ./mvnw to reduce layer size: we control the Maven version independently in Docker
RUN mvn -q --batch-mode -DskipTests -Dlicense.skip=true --also-make -pl zipkin-server package && \
    cp -rp docker/zipkin /zipkin && \
    (cp zipkin-server/target/zipkin-server-*-exec.jar /zipkin && cd /zipkin && jar xf *.jar && rm *.jar) && \
    cp -rp docker/zipkin /zipkin-slim && \
    (cp zipkin-server/target/zipkin-server-*-slim.jar /zipkin-slim && cd /zipkin-slim && jar xf *.jar && rm *.jar) && \
    # :zipkin-lens "npm run build" generates the build directory
    (mkdir -p /zipkin-lens && cp -r zipkin-lens/build/* /zipkin-lens/) && \
    # Delete /code to contain the image layer to static content, server binaries, ~/.m2 and ~/.npm
    cd / && rm -rf /code

# docker/hooks/post_push will republish what is otherwise built in docker/build/Dockerfile
# This is a copy/paste from builder/Dockerfile:
#
# zipkin-builder is JDK + artifact caches because DockerHub doesn't support another way to update
# cache between builds.
FROM openzipkin/java:${java_version} as zipkin-builder

COPY --from=built /root/.m2 /root/.m2
COPY --from=built /root/.npm /root/.npm

#####
# zipkin-ui - An image containing the Zipkin web frontend only, served by NGINX
#####

FROM nginx:1.18-alpine as zipkin-ui
LABEL MAINTAINER Zipkin "https://zipkin.io/"

ENV ZIPKIN_BASE_URL=http://zipkin:9411

COPY --from=built /zipkin-lens/ /var/www/html/zipkin/
RUN mkdir -p /var/tmp/nginx && chown -R nginx:nginx /var/tmp/nginx

# Setup services
COPY docker/lens/nginx.conf /etc/nginx/conf.d/zipkin.conf.template
COPY docker/lens/run.sh /usr/local/bin/nginx.sh

EXPOSE 80

CMD ["/usr/local/bin/nginx.sh"]

# Almost everything is common between the slim and normal build
FROM openzipkin/java:${java_version}-jre as base-server

# Use to set heap, trust store or other system properties.
ENV JAVA_OPTS -Djava.security.egd=file:/dev/./urandom

RUN adduser -g '' -h /zipkin -D zipkin

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
HEALTHCHECK --interval=5s --start-period=30s --timeout=5s CMD wget -qO- http://127.0.0.1:9411/health &> /dev/null || exit 1

ENTRYPOINT /zipkin/run.sh

#####
# zipkin-slim - An image containing the slim distribution of Zipkin server.
#####
FROM base-server as zipkin-slim

COPY --from=built --chown=zipkin /zipkin-slim/ /zipkin/

#####
# zipkin-server - An image containing the full distribution of Zipkin server.
#####
FROM base-server as zipkin-server

# 3rd party modules like zipkin-aws will apply profile settings with this
ENV MODULE_OPTS=

COPY --from=built --chown=zipkin /zipkin/ /zipkin/

# Zipkin's full distribution includes Scribe support (albeit disabled)
EXPOSE 9410 9411
