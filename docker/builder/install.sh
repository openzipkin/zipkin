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

echo "*** Installing Maven and dependencies"
# BusyBux built-in tar doesn't support --strip=1
# Allow boringssl for Netty per https://github.com/grpc/grpc-java/blob/master/SECURITY.md#netty
apk add --update --no-cache tar libc6-compat

# Use latest stable version here
MAVEN_VERSION=3.6.3
mkdir /usr/local/maven && cd /usr/local/maven
APACHE_MIRROR=$(wget -qO- https://www.apache.org/dyn/closer.cgi\?as_json\=1 | sed -n '/preferred/s/.*"\(.*\)"/\1/gp')
wget -qO- $APACHE_MIRROR/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xz --strip=1
ln -s /usr/local/maven/bin/mvn /usr/local/bin/mvn
