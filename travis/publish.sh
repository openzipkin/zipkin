#!/usr/bin/env bash
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

set -euo pipefail
set -x

build_started_by_tag() {
  if [ "${TRAVIS_TAG}" == "" ]; then
    echo "[Publishing] This build was not started by a tag, publishing snapshot"
    return 1
  else
    echo "[Publishing] This build was started by the tag ${TRAVIS_TAG}, publishing release"
    return 0
  fi
}

is_pull_request() {
  if [ "${TRAVIS_PULL_REQUEST}" != "false" ]; then
    echo "[Not Publishing] This is a Pull Request"
    return 0
  else
    echo "[Publishing] This is not a Pull Request"
    return 1
  fi
}

is_travis_branch_master() {
  if [ "${TRAVIS_BRANCH}" = master ]; then
    echo "[Publishing] Travis branch is master"
    return 0
  else
    echo "[Not Publishing] Travis branch is not master"
    return 1
  fi
}

check_travis_branch_equals_travis_tag() {
  #Weird comparison comparing branch to tag because when you 'git push --tags'
  #the branch somehow becomes the tag value
  #github issue: https://github.com/travis-ci/travis-ci/issues/1675
  if [ "${TRAVIS_BRANCH}" != "${TRAVIS_TAG}" ]; then
    echo "Travis branch does not equal Travis tag, which it should, bailing out."
    echo "  github issue: https://github.com/travis-ci/travis-ci/issues/1675"
    exit 1
  else
    echo "[Publishing] Branch (${TRAVIS_BRANCH}) same as Tag (${TRAVIS_TAG})"
  fi
}

check_release_tag() {
    tag="${TRAVIS_TAG}"
    if [[ "$tag" =~ ^[[:digit:]]+\.[[:digit:]]+\.[[:digit:]]+$ ]]; then
        echo "Build started by version tag $tag. During the release process tags like this"
        echo "are created by the 'release' Maven plugin. Nothing to do here."
        exit 0
    elif [[ ! "$tag" =~ ^release-[[:digit:]]+\.[[:digit:]]+\.[[:digit:]]+$ ]]; then
        echo "You must specify a tag of the format 'release-0.0.0' to release this project."
        echo "The provided tag ${tag} doesn't match that. Aborting."
        exit 1
    fi
}

release_version() {
    echo "${TRAVIS_TAG}" | sed 's/^release-//'
}

safe_checkout_master() {
  # We need to be on a branch for release:perform to be able to create commits, and we want that branch to be master.
  # But we also want to make sure that we build and release exactly the tagged version, so we verify that the remote
  # master is where our tag is.
  git checkout -B master
  git fetch origin master:origin/master
  commit_local_master="$(git show --pretty='format:%H' master)"
  commit_remote_master="$(git show --pretty='format:%H' origin/master)"
  if [ "$commit_local_master" != "$commit_remote_master" ]; then
    echo "Master on remote 'origin' has commits since the version under release, aborting"
    exit 1
  fi
}

#----------------------
# MAIN
#----------------------

if ! is_pull_request && build_started_by_tag; then
  check_travis_branch_equals_travis_tag
  check_release_tag
fi

MYSQL_USER=root ./mvnw install -nsu

if is_pull_request; then
  true
elif build_started_by_tag; then
  safe_checkout_master
  ./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -DreleaseVersion="$(release_version)" -Darguments="-DskipTests" release:prepare
  ./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -pl -:benchmarks,-:interop,-:centralsync-maven-plugin -DskipTests deploy
elif is_travis_branch_master; then
  ./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -pl -:benchmarks,-:interop,-:centralsync-maven-plugin -DskipTests deploy
fi

