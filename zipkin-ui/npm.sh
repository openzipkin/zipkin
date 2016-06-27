#!/bin/sh

TARGET="$(dirname $0)/target/node"

if [ ! -x "$TARGET/npm" ]; then
    echo "ERROR: npm not found at $TARGET/npm, did you run mvn install?"
    exit 1
fi

PATH="$TARGET:$PATH" npm "$@"
