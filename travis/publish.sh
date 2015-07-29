#!/bin/bash

echo "********************************"
echo "***** Publishing Artifacts *****"
echo "********************************"


function check_tag_exists(){
  if [ "${TRAVIS_TAG}" == "" ]; then
    echo "[Not Publishing] no release tags found"
    exit 1
  else
    echo "[Publishing] Tags found: ${TRAVIS_TAG}"
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

function publish_to_bintray(){
  echo "[Publishing] Create temporary Bintray credentials" &&
  sh "$TRAVIS_BUILD_DIR/.travis-ci-bintray-credentials.sh" &&
  echo "[Publishing] Starting Publish..." &&
  sbt 'set version := version.value + "." + System.getenv("TRAVIS_BUILD_NUMBER")' publish &&
  echo "[Publishing] Done"
}


#----------------------
# MAIN
#----------------------
check_tag_exists
check_is_pull_request
check_tag_valid
check_jdk_version
publish_to_bintray
