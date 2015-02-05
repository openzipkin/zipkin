#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION=@VERSION@
CONFIG=${DIR}/../config/collector-dev.scala
exec java -cp "$DIR/../libs/*" -jar $DIR/../zipkin-collector-service-$VERSION.jar -f $CONFIG
