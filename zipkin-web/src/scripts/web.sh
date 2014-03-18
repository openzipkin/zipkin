#!/bin/sh
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION=@VERSION@

java -cp "$DIR/../libs/*" -jar $DIR/../zipkin-web-$VERSION.jar $@
