#
# Copyright The OpenZipkin Authors
# SPDX-License-Identifier: Apache-2.0
#


# Use latest from https://hub.docker.com/r/apachepulsar/pulsar/tags
ARG pulsar_version=4.0.2

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

WORKDIR /docker-bin
COPY build-bin/docker/docker-healthcheck /docker-bin/

ARG pulsar_version

FROM apachepulsar/pulsar:${pulsar_version} as zipkin-pulsar
LABEL pulsar-version=$pulsar_version
LABEL org.opencontainers.image.description="Apache Pulsar on Alpine Linux"

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]

# Usually, we read env set from pid 1 to get docker-healthcheck parameters.
# However, pulsar-server has to start as root even if permissions are dropped
# later. So, we expose it in the Dockerfile instead.
ENV HEALTHCHECK_PORT=8080
ENV HEALTHCHECK_KIND=tcp
EXPOSE 8080 6650
CMD ["bin/pulsar", "standalone"]