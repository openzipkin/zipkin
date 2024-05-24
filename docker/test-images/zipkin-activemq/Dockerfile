#
# Copyright The OpenZipkin Authors
# SPDX-License-Identifier: Apache-2.0
#

# java_version is used for install and runtime layers of zipkin-activemq
#
# Use latest version here: https://github.com/orgs/openzipkin/packages/container/package/java
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG java_version=21.0.3_p9

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY build-bin/docker/docker-healthcheck /docker-bin/
COPY docker/test-images/zipkin-activemq/start-activemq /docker-bin/

FROM ghcr.io/openzipkin/java:${java_version} as install

WORKDIR /install

# Use latest version from https://activemq.apache.org/components/classic/download/
ARG activemq_version=6.1.2

# Download the distribution
RUN \
# Connection resets are frequent in GitHub Actions workflows \
wget --random-wait --tries=5 -qO- \
# Remove junk from the distribution while downloading it
https://archive.apache.org/dist/activemq/${activemq_version}/apache-activemq-${activemq_version}-bin.tar.gz| tar xz \
    --wildcards --exclude=examples --exclude=webapps-demo --strip=1

# Note: this uses the JDK image as ActiveMQ has a module dependency on JMX,
# which isn't in our JRE.
FROM ghcr.io/openzipkin/java:${java_version} as zipkin-activemq
LABEL org.opencontainers.image.description="ActiveMQ Classic on OpenJDK and Alpine Linux"
ARG activemq_version=6.1.2
LABEL activemq-version=$activemq_version

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENTRYPOINT ["start-activemq"]

# All content including binaries and logs write under WORKDIR
ARG USER=activemq
WORKDIR /${USER}
ENV ACTIVEMQ_HOME=/${USER}

# Ensure the process doesn't run as root
RUN adduser -g '' -h ${PWD} -D ${USER}
USER ${USER}

# Copy binaries and config we installed earlier
COPY --from=install --chown=${USER} /install .

# Use to set heap, trust store or other system properties.
ENV JAVA_OPTS="-Xms64m -Xmx64m -XX:+ExitOnOutOfMemoryError"
EXPOSE 1883 5672 8161 61613 61614 61616
