#
# Copyright 2015 The OpenZipkin Authors
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

FROM openzipkin/zipkin-base:base-1.21.0

MAINTAINER OpenZipkin "http://zipkin.io/"

VOLUME /tmp

ADD target/zipkin-java-server-0.1.0-SNAPSHOT-exec.jar zipkin-server.jar

RUN unzip zipkin-server.jar

EXPOSE 9411

CMD test -n "$STORAGE_TYPE" && source .${STORAGE_TYPE}_profile; java -Djava.security.egd=file:/dev/./urandom -cp '.:lib/*' io.zipkin.server.ZipkinServer
