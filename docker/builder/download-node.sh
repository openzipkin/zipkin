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

# This script downloads the nodejs archive into the Maven local repository so that
# frontend-maven-plugin can work on Alpine/musl. It mostly plays tricks to get rid
# of '-musl' in both the archive name and its contents.
#
# See https://github.com/eirslett/frontend-maven-plugin/pull/853
set -eux

# Get the version of node our build wants.
NODE_VERSION=$(mvn -pl zipkin-lens help:evaluate -Dexpression=node.version -q -DforceStdout)

# Get a local path corresponding to the Maven artifact the frontend-maven-plugin expects
NODE_MAVEN_PATH=~/.m2/repository/com/github/eirslett/node/${NODE_VERSION}/node-${NODE_VERSION}-linux-x64.tar.gz

if [ ! -f "${NODE_MAVEN_PATH}" ]; then
  # Get the URL of the unofficial build of musl
  NODE_DOWNLOAD_URL=https://unofficial-builds.nodejs.org/download/release/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64-musl.tar.gz
  echo "*** Downloading nodejs into the Maven local repository"

  # Strip '-musl' from the original archive's file paths
  mkdir /tmp/$$ && cd /tmp/$$
  wget -qO- ${NODE_DOWNLOAD_URL}| tar --transform 's/-musl//' -xz

  # Create a new archive where Maven would expect it
  mkdir -p $(dirname "${NODE_MAVEN_PATH}")
  tar -czf ${NODE_MAVEN_PATH} *
fi
