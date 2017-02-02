#!/bin/bash
#
# Copyright 2015-2017 The OpenZipkin Authors
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

# Due to https://issues.apache.org/jira/browse/MDEP-323 and cross-module dependencies,
# we can't easily run mvn dependency:go-offline. This is a workaround for that.
# It removes all dependencies on io.zipkin.java and ${project.groupId} using XSLT,
# then runs go-offline on the resulting POMs.

set -xeuo pipefail

rm -rf go-offline-builddir
mkdir -p go-offline-builddir
trap "rm -rf $(pwd)/go-offline-builddir" EXIT

for f in $(find . -name 'pom.xml'); do
    echo $f
    mkdir -p $(dirname go-offline-builddir/$f)
    xsltproc ./circleci/pom-no-crossmodule-dependencies.xsl $f > go-offline-builddir/$f
done

cd go-offline-builddir
../mvnw dependency:go-offline
