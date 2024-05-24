#
# Copyright The OpenZipkin Authors
# SPDX-License-Identifier: Apache-2.0
#

# Use latest from https://hub.docker.com/_/rabbitmq/tags?page=1&name=alpine
ARG rabbitmq_version=3.13.2

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

WORKDIR /docker-bin
COPY build-bin/docker/docker-healthcheck /docker-bin/
# Bootstrap config including a queue setup out-of-band, exported like so:
# rabbitmqctl export_definitions defs.json
COPY docker/test-images/zipkin-rabbitmq/config/* /config/

ARG rabbitmq_version

FROM rabbitmq:${rabbitmq_version}-alpine as zipkin-rabbitmq
LABEL rabbitmq-version=$rabbitmq_version
LABEL org.opencontainers.image.description="RabbitMQ on Alpine Linux"

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]

COPY --from=scratch /config/* /etc/rabbitmq/

RUN apk add --update --no-cache rabbitmq-c-utils

# Usually, we read env set from pid 1 to get docker-healthcheck parameters.
# However, rabbitmq-server has to start as root even if permissions are dropped
# later. So, we expose it in the Dockerfile instead.
ENV HEALTHCHECK_PORT=5672
ENV HEALTHCHECK_KIND=tcp
EXPOSE 5672
