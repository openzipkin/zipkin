#
# Copyright The OpenZipkin Authors
# SPDX-License-Identifier: Apache-2.0
#

# java_version is used for install and runtime layers of zipkin-elasticsearch7
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
COPY docker/test-images/zipkin-elasticsearch7/start-elasticsearch /docker-bin/
COPY docker/test-images/zipkin-elasticsearch7/config/ /config/

FROM ghcr.io/openzipkin/java:${java_version} as install

WORKDIR /install

# Use latest 7.x version from https://www.elastic.co/downloads/past-releases#elasticsearch-no-jdk
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG elasticsearch7_version=7.17.21

# Download only the OSS distribution (lacks X-Pack)
RUN \
# Connection resets are frequent in GitHub Actions workflows \
wget --random-wait --tries=5 -qO- \
# We don't download bin scripts as we customize for reasons including BusyBox problems
https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${elasticsearch7_version}-no-jdk-linux-x86_64.tar.gz| tar xz \
    --wildcards --strip=1 --exclude=*/bin && mkdir classes
COPY --from=scratch /config/ ./config/

# Use a full Java distribution rather than adding test modules to the
# production -jre base layer used by zipkin and zipkin-slim.
FROM ghcr.io/openzipkin/java:${java_version} as zipkin-elasticsearch7
LABEL org.opencontainers.image.description="Elasticsearch distribution on OpenJDK and Alpine Linux"
ARG elasticsearch7_version=7.17.21
LABEL elasticsearch-version=$elasticsearch7_version

# The full license is also included in the image at /elasticsearch/LICENSE.txt.
LABEL org.opencontainers.image.licenses="Elastic-License-2.0"

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENTRYPOINT ["start-elasticsearch"]

# All content including binaries and logs write under WORKDIR
ARG USER=elasticsearch
WORKDIR /${USER}

# Ensure the process doesn't run as root
RUN adduser -g '' -h ${PWD} -D ${USER}
USER ${USER}

# Copy binaries and config we installed earlier
COPY --from=install --chown=${USER} /install .

# Use to set heap, trust store or other system properties.
ENV JAVA_OPTS="-Xms512m -Xmx512m -XX:+ExitOnOutOfMemoryError"
ENV LIBFFI_TMPDIR=/tmp
EXPOSE 9200
