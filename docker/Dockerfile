#
# Copyright 2015-2019 The OpenZipkin Authors
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

FROM openzipkin/zipkin-builder

COPY . /code
RUN cd /code && mvn -B --no-transfer-progress package -DskipTests=true -pl zipkin-server -am

FROM gcr.io/distroless/java:11-debug
LABEL MAINTAINER Zipkin "https://zipkin.io/"

# Use to set heap, trust store or other system properties.
ENV JAVA_OPTS -Djava.security.egd=file:/dev/./urandom
# 3rd party modules like zipkin-aws will apply profile settings with this
ENV MODULE_OPTS=

RUN ["/busybox/sh", "-c", "adduser -g '' -h /zipkin -D zipkin"]

# Add environment settings for supported storage types
COPY --from=0 --chown=zipkin /code/zipkin-server/target/zipkin-server-*-exec.jar /zipkin/
COPY --chown=zipkin docker/zipkin/ /zipkin/
WORKDIR /zipkin

RUN ["/busybox/sh", "-c", "ln -s /busybox/* /bin"]

USER zipkin

EXPOSE 9410 9411

ENTRYPOINT ["/busybox/sh", "run.sh"]
