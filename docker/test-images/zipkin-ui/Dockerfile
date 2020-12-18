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

# Use latest version here: https://github.com/orgs/openzipkin/packages/container/package/alpine
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG alpine_version=3.12.3

# java_version is used during the installation process to build or download the zipkin-lens jar.
#
# Use latest version here: https://github.com/orgs/openzipkin/packages/container/package/java
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG java_version=15.0.1_p9

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY build-bin/ /build-bin/
COPY build-bin/docker/docker-healthcheck /docker-bin/
COPY docker/test-images/zipkin-ui/start-nginx /docker-bin/
COPY pom.xml /code/
COPY zipkin-lens/ /code/zipkin-lens/
COPY docker/test-images/zipkin-ui/nginx.conf /conf.d/zipkin.conf.template

# This version is only used during the install process. Try to be consistent as it reduces layers,
# which reduces downloads.
FROM ghcr.io/openzipkin/java:${java_version} as install

COPY --from=scratch /build-bin/ /build-bin/

WORKDIR /code
# Conditions aren't supported in Dockerfile instructions, so we copy source even if it isn't used.
COPY --from=scratch /code/ .

WORKDIR /install

# When true, build-bin/maven/unjar searches /code for the artifact instead of resolving remotely.
# /code contains what is allowed in .dockerignore. On problem, ensure .dockerignore is correct.
ARG release_from_maven_build=false
ENV RELEASE_FROM_MAVEN_BUILD=$release_from_maven_build
# Version of the artifact to unjar. Ex. "2.4.5" or "2.4.5-SNAPSHOT" "master" to use the pom version.
ARG version=master
ENV VERSION=$version
ENV MAVEN_PROJECT_BASEDIR=/code/zipkin-lens
RUN /build-bin/maven/maven_build_or_unjar io.zipkin zipkin-lens ${VERSION}

FROM ghcr.io/openzipkin/alpine:$alpine_version as zipkin-ui
LABEL org.opencontainers.image.description="NGINX on Alpine Linux hosting the Zipkin UI with Zipkin API proxy_pass"
# Use latest from https://pkgs.alpinelinux.org/packages?name=nginx
ARG nginx_version=1.18.0
LABEL nginx-version=$nginx_version

ENV ZIPKIN_BASE_URL=http://zipkin:9411

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENTRYPOINT ["start-nginx"]

# Add content and setup NGINX
COPY --from=install /install/zipkin-lens/ /var/www/html/zipkin/
COPY --from=scratch /conf.d/ /etc/nginx/conf.d/
RUN apk add --update --no-cache nginx=~${nginx_version} && \
    mkdir -p /var/tmp/nginx && chown -R nginx:nginx /var/tmp/nginx

# Usually, we read env set from pid 1 to get docker-healthcheck parameters. However, NGINX wipes the
# env, so we need to expose it in the Dockerfile instead.
ENV HEALTHCHECK_PORT=80
EXPOSE 80
