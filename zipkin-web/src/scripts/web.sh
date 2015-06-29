#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION=@VERSION@

exec java -cp "$DIR/../libs/*" -jar $DIR/../zipkin-web-$VERSION.jar -zipkin.web.resourcesRoot=$DIR/../resources/ $@
