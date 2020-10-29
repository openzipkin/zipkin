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

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY . /code/

FROM ghcr.io/openzipkin/java:${java_version} as built

# Copy Maven source tree into the Docker context
COPY --from=scratch /code /code

WORKDIR /code

# Run our script to hydrate the Maven and NPM cache
RUN /code/build-bin/go_offline

# zipkin-builder is JDK + artifact caches because DockerHub doesn't support another way to update
# cache between builds.
FROM ghcr.io/openzipkin/java:${java_version} as zipkin-builder

COPY --from=built /root/.m2 /root/.m2
COPY --from=built /root/.npm /root/.npm
