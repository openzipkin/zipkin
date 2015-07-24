#!/bin/bash


set -e

dir=/tmp/zipkin.$$
trap "rm -fr $dir" 0 1 2

echo 'making site...' 1>&2
./bin/sbt zipkin-doc/make-site >/dev/null 2>&1

echo 'cloning...' 1>&2
git clone -b gh-pages git@github.com:twitter/zipkin.git $dir >/dev/null 2>&1

savedir=$(pwd)
echo $savedir
cd $dir
git rm -fr .
touch .nojekyll
#cp -r $savedir/target/scala-2.10/unidoc/ docs
cp -r $savedir/doc/target/site/* .
git add -f .
git diff-index --quiet HEAD || (git commit -am"site push by $(whoami)"; git push origin gh-pages:gh-pages;)
