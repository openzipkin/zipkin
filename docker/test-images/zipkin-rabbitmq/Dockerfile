#
# Copyright 2015-2023 The OpenZipkin Authors
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
ARG alpine_version=3.19.0

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

WORKDIR /docker-bin
COPY build-bin/docker/docker-healthcheck /docker-bin/
COPY docker/test-images/zipkin-rabbitmq/start-rabbitmq /docker-bin/
COPY docker/test-images/zipkin-rabbitmq/install.sh /install/

FROM ghcr.io/openzipkin/alpine:${alpine_version} as zipkin-rabbitmq
LABEL org.opencontainers.image.description="RabbitMQ on Alpine Linux"

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENTRYPOINT ["start-rabbitmq"]

# Use latest from https://pkgs.alpinelinux.org/packages?name=rabbitmq-server
ARG rabbitmq_version=3.12.8
LABEL rabbitmq-version=$rabbitmq_version
ENV RABBITMQ_SERVER_VERSION=$rabbitmq_version
# Use latest from https://pkgs.alpinelinux.org/packages?name=rabbitmq-c-utils
ARG rabbitmq_c_utils_version=0.13.0
ENV RABBITMQ_C_UTILS_VERSION=$rabbitmq_c_utils_version

COPY --from=scratch /install/install.sh /tmp/install/

ARG USER=rabbitmq
# We don't set USER because rabbitmq-server needs to start as root.
RUN adduser -g '' -h ${PWD} -D ${USER} && \
    (cd /tmp/install && ./install.sh) && rm -rf /tmp/install

# Usually, we read env set from pid 1 to get docker-healthcheck parameters.
# However, rabbitmq-server has to start as root even if permissions are dropped
# later. So, we expose it in the Dockerfile instead.
ENV HEALTHCHECK_PORT=5672
ENV HEALTHCHECK_KIND=tcp
EXPOSE 5672
