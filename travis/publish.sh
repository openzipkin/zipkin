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

build_started_by_tag() {
  if [ "${TRAVIS_TAG}" == "" ]; then
    echo "[Publishing] This build was not started by a tag, publishing publishing snapshot"
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

check_tag_equals_version_in_pom() {
    snapshot_version_in_pom="$(./mvnw -Dexec.executable='echo' -Dexec.args='${project.version}' --non-recursive exec:exec | grep -Ev '(^\[|Download\w+:)')"
    version_in_pom="$(echo "${snapshot_version_in_pom}" | sed 's/-SNAPSHOT$//')"
    tag="${TRAVIS_TAG}"
    version_in_tag="$(echo "${tag}" | sed 's/^release-//')"

    if [ "$version_in_pom" != "$version_in_tag" ]; then
        echo "Version in pom.xml doesn't match version in git tag, bailing out."
        echo "  Snapshot Version parsed from pom.xml: ${snapshot_version_in_pom}"
        echo "  Release version parsed from pom.xml: ${version_in_pom}"
        echo "  Git tag: ${tag}"
        echo "  Release version in git tag: ${version_in_tag}"
        exit 1
    else
        echo "Version in pom.xml matches git tag (${version_in_tag})"
    fi
}

#----------------------
# MAIN
#----------------------

if ! is_pull_request && build_started_by_tag; then
  check_travis_branch_equals_travis_tag
  check_release_tag
  check_tag_equals_version_in_pom
fi

MYSQL_USER=root ./mvnw install -nsu

if is_pull_request; then
  true
elif build_started_by_tag; then
  ./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu release:prepare
  ./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -pl -:benchmarks,-:interop,-:centralsync-maven-plugin release:perform
elif is_travis_branch_master; then
  ./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu deploy
fi

