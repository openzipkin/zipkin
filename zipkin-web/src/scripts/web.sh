#!/bin/sh
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION=@VERSION@
CONFIG=${DIR}/../config/web-dev.scala

java -cp "$DIR/../libs/*" -jar $DIR/../zipkin-web-$VERSION.jar -f $CONFIG -D local_docroot=zipkin-web/src