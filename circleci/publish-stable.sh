#!/bin/bash
#
# Copyright 2015-2016 The OpenZipkin Authors
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

set -xueo pipefail

# We need to be on a branch for release:perform to be able to create
# commits, and we want that branch to be master. But we also want to make
# sure that we build and release exactly the tagged version, so we verify
# that the remote master is where our tag is.
git checkout -B master
git fetch origin master:origin/master
commit_local_master="$(git show --pretty='format:%H' master)"
commit_remote_master="$(git show --pretty='format:%H' origin/master)"
if [ "$commit_local_master" != "$commit_remote_master" ]; then
    echo "Master on remote 'origin' has commits since the version under release, aborting"
    exit 1
fi

# This creates
#   - A commit in git that removes the `-SNAPSHOT` from the version.
#     This commit will not trigger a separate build due to the [skip ci] in the
#     commit message.
#   - A tag `X.Y.Z`. This will also not trigger another build, because on
#     CircleCI only tags with a matching `deployment` section in their
#     circle.yml trigger builds
./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu \
       -DreleaseVersion="$(echo $CIRCLE_TAG | sed 's/^release-//')" \
       -DscmCommentPrefix='[maven-release-plugin][skip ci] ' \
       -Darguments="-DskipTests" \
       release:prepare

# This next line will
#   - Publish the project to Bintray
#   - Create a commit bumping versions to the next version and adding -SNAPSHOT.
#     This commit will not trigger a separate build due to the [skip ci] in the
#     commit message.
./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -pl -:benchmarks \
       -DskipTests -DscmCommentPrefix='[maven-release-plugin][skip ci] ' \
       deploy

# And this syncs it to Maven Central. Note: this needs to be done once per
# project, not module, hence -N
./mvnw --batch-mode -s ./.settings.xml -nsu -N \
       io.zipkin.centralsync-maven-plugin:centralsync-maven-plugin:sync
