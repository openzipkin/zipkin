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

ARG java_version
ARG nginx_version

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY . /code/

FROM ghcr.io/openzipkin/java:${java_version} as install

WORKDIR /install

# Conditions aren't supported in Dockerfile instructions, so we copy source even if it isn't used.
COPY --from=scratch /code /code

# This will either be "master" or a real version ex. "2.4.5"
ARG release_version
ENV RELEASE_VERSION=$release_version
# When true, main images reuse zipkin-exec.jar and zipkin-slim.jar in the context root
ARG release_from_maven_build=false
ENV RELEASE_FROM_MAVEN_BUILD=$release_from_maven_build
RUN /code/docker/bin/install.sh

# Use a quay.io mirror to prevent build outages due to Docker Hub pull quotas
FROM quay.io/app-sre/nginx:$nginx_version-alpine as zipkin-ui

ARG maintainer="OpenZipkin https://gitter.im/openzipkin/zipkin"
LABEL maintainer=$maintainer
LABEL org.opencontainers.image.authors=$maintainer
LABEL org.opencontainers.image.description="NGINX on Alpine Linux hosting the Zipkin UI with Zipkin API proxy_pass"

ENV ZIPKIN_BASE_URL=http://zipkin:9411

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=install /code/docker/lens/docker-bin/ /tmp/docker-bin/
RUN mv /tmp/docker-bin/* /usr/local/bin/ && rm -rf /tmp/docker-bin
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
CMD ["start-nginx"]

# Add content and setup NGINX
COPY --from=install /install/zipkin-lens/ /var/www/html/zipkin/
COPY --from=install /code/docker/lens/nginx.conf /etc/nginx/conf.d/zipkin.conf.template
RUN mkdir -p /var/tmp/nginx && chown -R nginx:nginx /var/tmp/nginx

EXPOSE 80

# Almost everything is common between the slim and normal build
FROM ghcr.io/openzipkin/java:${java_version}-jre as base-server

# All content including binaries and logs write under WORKDIR
ARG USER=zipkin
WORKDIR /${USER}

# Ensure the process doesn't run as root
RUN adduser -g '' -h ${PWD} -D ${USER}

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=install /code/docker/bin/ /tmp/docker-bin/
RUN mv /tmp/docker-bin/docker-healthcheck /usr/local/bin/ && \
    mv /tmp/docker-bin/start-zipkin /usr/local/bin/ && \
    rm -rf /tmp/docker-bin
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts.
#
# If in production, you have a 30s startup, please report to https://gitter.im/openzipkin/zipkin
# including the values of the /health and /info endpoints as this would be unexpected.
HEALTHCHECK --interval=5s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]

ENTRYPOINT ["start-zipkin"]

# Switch to the runtime user
USER ${USER}

EXPOSE 9411

FROM base-server as zipkin-slim
LABEL org.opencontainers.image.description="Zipkin slim distribution on OpenJDK and Alpine Linux"

COPY --from=install --chown=${USER} /install/zipkin-slim/ /zipkin/

FROM base-server as zipkin
LABEL org.opencontainers.image.description="Zipkin full distribution on OpenJDK and Alpine Linux"

# 3rd party modules like zipkin-aws will apply profile settings with this
ENV MODULE_OPTS=

COPY --from=install --chown=${USER} /install/zipkin/ /zipkin/

# Zipkin's full distribution includes Scribe support (albeit disabled)
EXPOSE 9410 9411
