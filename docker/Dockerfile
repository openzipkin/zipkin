#
# Copyright 2015-2021 The OpenZipkin Authors
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

# java_version is used for install and runtime base layers of zipkin and zipkin-slim.
#
# Use latest version here: https://github.com/orgs/openzipkin/packages/container/package/java
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG java_version=15.0.7_p4

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY build-bin/docker/docker-healthcheck /docker-bin/
COPY docker/start-zipkin /docker-bin/
COPY . /code/

# This version is only used during the install process. Try to be consistent as it reduces layers,
# which reduces downloads.
FROM ghcr.io/openzipkin/java:${java_version} as install

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
ENV MAVEN_PROJECT_BASEDIR=/code
RUN /code/build-bin/maven/maven_build_or_unjar io.zipkin zipkin-server ${VERSION} exec && \
    mv zipkin-server zipkin && \
    /code/build-bin/maven/maven_build_or_unjar io.zipkin zipkin-server ${VERSION} slim && \
    mv zipkin-server zipkin-slim

# Almost everything is common between the slim and normal build
FROM ghcr.io/openzipkin/java:${java_version}-jre as base-server

# All content including binaries and logs write under WORKDIR
ARG USER=zipkin
WORKDIR /${USER}

# Ensure the process doesn't run as root
RUN adduser -g '' -h ${PWD} -D ${USER}

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts.
#
# If in production, you have a 30s startup, please report to https://gitter.im/openzipkin/zipkin
# including the values of the /health and /info endpoints as this would be unexpected.
HEALTHCHECK --interval=5s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]

ENTRYPOINT ["start-zipkin"]

# Switch to the runtime user
USER ${USER}

FROM base-server as zipkin-slim
LABEL org.opencontainers.image.description="Zipkin slim distribution on OpenJDK and Alpine Linux"

COPY --from=install --chown=${USER} /install/zipkin-slim/ /zipkin/

EXPOSE 9411

FROM base-server as zipkin
LABEL org.opencontainers.image.description="Zipkin full distribution on OpenJDK and Alpine Linux"

# 3rd party modules like zipkin-aws will apply profile settings with this
ENV MODULE_OPTS=

COPY --from=install --chown=${USER} /install/zipkin/ /zipkin/

# Zipkin's full distribution includes Scribe support (albeit disabled)
EXPOSE 9410 9411
