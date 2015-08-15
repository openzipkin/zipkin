#!/usr/bin/env bash
set -ex

declare -r PUBLISH_USING_JDK="oraclejdk7"

function increment_version() {
  # TODO this would be cleaner in release.versionPatterns
  local v=$1
  if [ -z $2 ]; then
     local rgx='^((?:[0-9]+\.)*)([0-9]+)($)'
  else
     local rgx='^((?:[0-9]+\.){'$(($2-1))'})([0-9]+)(\.|$)'
     for (( p=`grep -o "\."<<<".$v"|wc -l`; p<$2; p++)); do
        v+=.0; done; fi
  val=`echo -e "$v" | perl -pe 's/^.*'$rgx'.*$/$2/'`
  echo "$v" | perl -pe s/$rgx.*$'/${1}'`printf %0${#val}s $(($val+1))`/
}

function build_started_by_tag(){
  if [ "${TRAVIS_TAG}" == "" ]; then
    echo "[Publishing] This build was not started by a tag, publishing"
    return 1
  else
    echo "[Publishing] This build was started by the tag ${TRAVIS_TAG}, creating release commits"
    return 0
  fi
}

function is_pull_request(){
  if [ "${TRAVIS_PULL_REQUEST}" != "false" ]; then
    echo "[Not Publishing] This is a Pull Request"
    return 0
  else
    echo "[Publishing] This is not a Pull Request"
    return 1
  fi
}

function is_travis_branch_master(){
  if [ "${TRAVIS_BRANCH}" = master ]; then
    echo "[Publishing] Travis branch is master"
    return 0
  else
    echo "[Not Publishing] Travis branch is not master"
    return 1
  fi
}

function check_travis_branch_equals_travis_tag(){
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

function want_to_release_from_this_jdk(){
  if [ "${TRAVIS_JDK_VERSION}" != "${PUBLISH_USING_JDK}" ]; then
    echo "[Not Publishing] Current JDK(${TRAVIS_JDK_VERSION}) does not"
    echo "[Not Publishing]   equal PUBLISH_USING_JDK(${PUBLISH_USING_JDK})"
    return 1
  else
    echo "[Publishing] Current JDK is the same as PUBLISH_USING_JDK"
    echo "[Publishing]   environment variable (${TRAVIS_JDK_VERSION})"
    return 0
  fi
}

function publish(){
  echo "[Publishing] Publishing..."
  ./gradlew check zipkinUpload
  echo "[Publishing] Done"
}

function do_gradle_release(){
  # TODO this would be cleaner in release.versionPatterns
  major_minor_revision=$(echo "$TRAVIS_TAG" | cut -f1 -d-)
  qualifier=$(echo "$TRAVIS_TAG" | cut -f2 -d- -s)

  # do not increment if the version is tentative ex. 1.0.0-rc1
  if [[ -n "$qualifier" ]]; then
    new_version=${major_minor_revision}
  else
    new_version=$(increment_version "${major_minor_revision}")
  fi
  new_version="${new_version}-SNAPSHOT"

  echo "[Publishing] Creating release commits"
  echo "[Publishing]   Release version: ${TRAVIS_TAG}"
  echo "[Publishing]   Post-release version: ${new_version}"

  git checkout -B master

  ./gradlew check \
            release -Prelease.useAutomaticVersion=true -PreleaseVersion=${TRAVIS_TAG} -PnewVersion=${new_version}
  echo "[Publishing] Done"
}

function run_tests(){
  echo "[Not Publishing] Running tests then exiting."
  ./gradlew check
}

#----------------------
# MAIN
#----------------------
action=run_tests
if want_to_release_from_this_jdk && ! is_pull_request; then
  if build_started_by_tag; then
    check_travis_branch_equals_travis_tag
    action=do_gradle_release
  elif is_travis_branch_master; then
    action=publish
  fi
fi

$action

