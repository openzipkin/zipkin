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

# Use latest version here: https://github.com/orgs/openzipkin/packages/container/package/alpine
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG alpine_version=3.14.3

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY build-bin/docker/docker-healthcheck /docker-bin/
COPY docker/test-images/zipkin-mysql/start-mysql /docker-bin/
COPY docker/test-images/zipkin-mysql/install.sh /install/
COPY zipkin-storage/mysql-v1/src/main/resources/mysql.sql /zipkin-schemas/

FROM ghcr.io/openzipkin/alpine:${alpine_version} as zipkin-mysql
LABEL org.opencontainers.image.description="MySQL on Alpine Linux with Zipkin schema pre-installed"

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENTRYPOINT ["start-mysql"]

# Use latest from https://pkgs.alpinelinux.org/packages?name=mysql
ARG mysql_version=10.6.8
LABEL mysql-version=$mysql_version
ENV MYSQL_VERSION=$mysql_version

WORKDIR /tmp
COPY --from=scratch /zipkin-schemas/* ./install/zipkin-schemas/
COPY --from=scratch /install/install.sh ./install
RUN (cd install && ./install.sh) && rm -rf ./install

# All content including binaries and logs write under WORKDIR
ARG USER=mysql
WORKDIR /${USER}

EXPOSE 3306
