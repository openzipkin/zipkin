#
# Copyright The OpenZipkin Authors
# SPDX-License-Identifier: Apache-2.0
#

# java_version is used for install and runtime base layers of eureka and eureka-slim.
#
# Use latest version here: https://github.com/orgs/openeureka/packages/container/package/java
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG java_version=21.0.3_p9

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY build-bin/docker/docker-healthcheck /docker-bin/
COPY docker/test-images/zipkin-eureka/start-eureka /docker-bin/

# We needsource because as of Eureka 2.0, there is no distribution in the
# source repository anymore, and the war isn't functional either. The current
# approach is to build your own with Spring Cloud.
#
# See https://github.com/Netflix/eureka/releases/tag/v2.0.0
COPY build-bin/maven /code/
COPY docker/test-images/zipkin-eureka /code/

# This version is only used during the install process. Try to be consistent as it reduces layers,
# which reduces downloads.
FROM ghcr.io/openzipkin/java:${java_version} as install

WORKDIR /code
COPY --from=scratch /code/ .

# Build the custom Eureka server
RUN /code/maven_build && \
    mvn -q --batch-mode -DoutputDirectory=/install/lib dependency:copy-dependencies && \
    cp -r target/classes /install/

# Use a full Java distribution rather than adding test modules to the
# production -jre base layer used by zipkin and zipkin-slim.
#
# Specifically, this is about NoClassDefFoundError: org/ietf/jgss/GSSException
FROM ghcr.io/openzipkin/java:${java_version} as zipkin-eureka
LABEL org.opencontainers.image.description="Eureka on OpenJDK and Alpine Linux"

# All content including binaries and logs write under WORKDIR
ARG USER=eureka
WORKDIR /${USER}

# Ensure the process doesn't run as root
RUN adduser -g '' -h ${PWD} -D ${USER}

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/

# Copy binaries and config we installed earlier
COPY --from=install --chown=${USER} /install .

# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENV HEALTHCHECK_PATH=/actuator/health
ENV HEALTHCHECK_PORT=8761

ENTRYPOINT ["start-eureka"]

# Switch to the runtime user
USER ${USER}

# Use to set heap, trust store or other system properties.
ENV JAVA_OPTS="-Xms64m -Xmx64m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8761
