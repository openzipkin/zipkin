#!/usr/bin/env bash
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

print_project_version() {
  ./mvnw help:evaluate -N -Dexpression=project.version|sed -n '/^[0-9]/p'
}

is_release_commit() {
  project_version="$(print_project_version)"
  if [[ "$project_version" =~ ^[[:digit:]]+\.[[:digit:]]+\.[[:digit:]]+$ ]]; then
    echo "Build started by release commit $project_version. Will synchronize to maven central."
    return 0
  else
    return 1
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

javadoc_to_gh_pages() {
  version="$(print_project_version)"
  rm -rf javadoc-builddir
  builddir="javadoc-builddir/$version"

  # Collect javadoc for all modules
  for jar in $(find . -name "*${version}-javadoc.jar"); do
    module="$(echo "$jar" | sed "s~.*/\(.*\)-${version}-javadoc.jar~\1~")"
    this_builddir="$builddir/$module"
    if [ -d "$this_builddir" ]; then
        # Skip modules we've already processed.
        # We may find multiple instances of the same javadoc jar because of, for instance,
        # integration tests copying jars around.
        continue
    fi
    mkdir -p "$this_builddir"
    unzip "$jar" -d "$this_builddir"
    # Build a simple module-level index
    echo "<li><a href=\"${module}/index.html\">${module}</a></li>" >> "${builddir}/index.html"
  done

  # Update gh-pages
  git fetch origin gh-pages:gh-pages
  git checkout gh-pages
  rm -rf "$version"
  mv "javadoc-builddir/$version" ./
  rm -rf "javadoc-builddir"

  # Update simple version-level index
  if ! grep "$version" index.html 2>/dev/null; then
    echo "<li><a href=\"${version}/index.html\">${version}</a></li>" >> index.html
  fi

  # Ensure links are ordered by versions, latest on top
  sort -rV index.html > index.html.sorted
  mv index.html.sorted index.html

  git add "$version"
  git add index.html
  git commit -m "Automatically updated javadocs for $version"
  git push origin gh-pages
}

#----------------------
# MAIN
#----------------------

if ! is_pull_request && build_started_by_tag; then
  check_travis_branch_equals_travis_tag
  check_release_tag
fi

# skip license on travis due to #1512
MYSQL_USER=root ./mvnw install -nsu -Dlicense.skip=true

# If we are on a pull request, our only job is to run tests, which happened above via ./mvnw install
if is_pull_request; then
  true

# If we are on master, we will deploy the latest snapshot or release version
#   - If a release commit fails to deploy for a transient reason, delete the broken version from bintray and click rebuild
elif is_travis_branch_master; then
  ./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -pl -:benchmarks -DskipTests deploy

  # If the deployment succeeded, sync it to Maven Central. Note: this needs to be done once per project, not module, hence -N
  if is_release_commit; then
    ./mvnw --batch-mode -s ./.settings.xml -nsu -N io.zipkin.centralsync-maven-plugin:centralsync-maven-plugin:sync
    javadoc_to_gh_pages
  fi

# If we are on a release tag, the following will update any version references and push a version tag for deployment.
elif build_started_by_tag; then
  safe_checkout_master
  # skip license on travis due to #1512
  ./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -DreleaseVersion="$(release_version)" -Darguments="-DskipTests -Dlicense.skip=true" release:prepare
fi
