#!/bin/bash

set -ueo pipefail

releaseid=$(git rev-parse HEAD)
if [ -z "$releaseid" ]; then
    echo "git rev-parse HEAD did not give back anything useful, aborting" >&2
    exit 1
fi

$(dirname $0)/sbt zipkin-{web,collector-service,query-service}/assembly zipkin-web/package-dist

temp=$(mktemp -d)
trap "cd $(pwd); rm -rf ${temp}" EXIT

cp -v zipkin-{web,collector-service,query-service}/target/*-assembly-*-SNAPSHOT.jar $temp/
cp -v zipkin-web/dist/zipkin-web.zip $temp/

cd $temp/
for f in *; do
    sha256sum -b $f | cut -d' ' -f1 > $f.sha256
done

aws s3 cp --acl public-read --recursive . s3://prezireleases/zipkin/$releaseid/
