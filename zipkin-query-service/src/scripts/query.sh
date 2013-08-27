#!/bin/sh
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION=@VERSION@
CONFIG=${DIR}/../config/query-dev.scala
java -cp "$DIR/../libs/*" -jar $DIR/../zipkin-query-service-$VERSION.jar -f ${CONFIG}