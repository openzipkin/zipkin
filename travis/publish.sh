#!/bin/bash

echo "********************************"
echo "***** Publishing Artifacts *****"
echo "********************************"

declare -r PUBLISH_USING_JDK="oraclejdk7"

function increment_version() {
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

function publish_release(){
  if [ "${TRAVIS_TAG}" == "" ]; then
    echo "[Publishing] snapshot release"
    return 1
  else
    echo "[Publishing] tag: ${TRAVIS_TAG}"
    return 0
  fi
}

function check_is_pull_request(){
  if [ "${TRAVIS_PULL_REQUEST}" == "true" ]; then
    echo "[Not Publishing] this is a Pull Request"
    exit 1
  else
    echo "[Publishing] this is not a Pull Request"
  fi
}

function check_tag_valid(){
  #Weird comparison comparing branch to tag because when you 'git push --tags'
  #the branch somehow becomes the tag value
  #github issue: https://github.com/travis-ci/travis-ci/issues/1675
  if [ "${TRAVIS_BRANCH}" != "${TRAVIS_TAG}" ]; then
    echo "[Not Publishing] Travis branch does not equal Travis tag, which it should: "
    echo "[Not Publishing]   github issue: https://github.com/travis-ci/travis-ci/issues/1675"
    exit 1
  else
    echo "[Publishing] Branch (${TRAVIS_BRANCH}) same as Tag (${TRAVIS_TAG})"
  fi
}

function check_jdk_version(){
  if [ "${TRAVIS_JDK_VERSION}" != "${PUBLISH_USING_JDK}" ]; then
    echo "[Not Publishing] Current JDK(${TRAVIS_JDK_VERSION}) does not"
    echo "[Not Publishing]   equal PUBLISH_USING_JDK(${PUBLISH_USING_JDK})"
    exit 1
  else
    echo "[Publishing] Current JDK is the same as PUBLISH_USING_JDK"
    echo "[Publishing]   environment variable (${TRAVIS_JDK_VERSION})"
  fi
}

function publish_snapshots_to_bintray(){
  echo "[Publishing] Starting Snapshot Publish..."
  ./gradlew bintrayUpload
  echo "[Publishing] Done"
}

function publish_release_to_bintray(){
  new_version=`increment_version "${TRAVIS_TAG}"`
  echo "[Publishing] Starting Release Publish (${TRAVIS_TAG}) new version (${new_version})..."
  ./gradlew release -Prelease.useAutomaticVersion=true -PreleaseVersion=${TRAVIS_TAG} -PnewVersion=${new_version}-SNAPSHOT
  ./gradlew bintrayUpload
  echo "[Publishing] Done"
}

#----------------------
# MAIN
#----------------------
git checkout -b master

check_is_pull_request
check_jdk_version

if publish_release; then
  check_tag_valid
  publish_release_to_bintray
else
  publish_snapshots_to_bintray
fi
