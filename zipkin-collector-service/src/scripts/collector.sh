#!/bin/sh
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION=@VERSION@
CONFIG=${DIR}/../config/collector-dev.scala
java -cp "$DIR/../libs/*" -jar $DIR/../zipkin-collector-service-$VERSION.jar -f $CONFIG
