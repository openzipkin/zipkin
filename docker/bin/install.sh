#!/bin/sh
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

set -eux

# This script decides based on $ZIPKIN_FROM_MAVEN_BUILD and $ZIPKIN_VERSION whether to reuse or
# download the binaries we need.
if [ "$ZIPKIN_FROM_MAVEN_BUILD" = "true" ]; then
  echo "*** Reusing Zipkin jars in the Docker context..."
  cp "/code/zipkin-server/target/zipkin-server-${ZIPKIN_VERSION}-exec.jar" zipkin-exec.jar
  cp "/code/zipkin-server/target/zipkin-server-${ZIPKIN_VERSION}-slim.jar" zipkin-slim.jar
else
  case ${ZIPKIN_VERSION} in
    *-SNAPSHOT )
      echo "Building from source within Docker is not supported. \
            Build via instructions at the bottom of zipkin-server/README.md \
            and set ZIPKIN_FROM_MAVEN_BUILD=true"
      exit 1
      ;;
    * )
      echo "*** Downloading from Maven..."
      for classifier in exec slim; do
        mvn -q --batch-mode --batch-mode org.apache.maven.plugins:maven-dependency-plugin:3.1.2:get \
            -Dtransitive=false -Dartifact=io.zipkin:zipkin-server:${ZIPKIN_VERSION}:jar:${classifier}
        find ~/.m2/repository -name zipkin-server-${ZIPKIN_VERSION}-${classifier}.jar -exec cp {} zipkin-${classifier}.jar \;
      done
      ;;
    esac
fi

# sanity check!
test -f zipkin-exec.jar
test -f zipkin-slim.jar

mkdir zipkin zipkin-slim zipkin-lens
(mv zipkin-exec.jar zipkin/ && cd zipkin && jar xf *.jar && rm *.jar)
(mv zipkin-slim.jar zipkin-slim/ && cd zipkin-slim && jar xf *.jar && rm *.jar)
(jar xf zipkin/BOOT-INF/lib/zipkin-lens-*.jar && rm -rf zipkin-lens/META-INF)
