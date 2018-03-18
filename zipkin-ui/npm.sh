#!/bin/sh
TARGET="$(dirname $0)/target/node"

if [ ! -x "$TARGET/node" ]; then
    echo "ERROR: node not found at $TARGET/node, did you run mvn install?"
    exit 1
fi

PATH=$TARGET:$PATH
node "target/node/node_modules/npm/bin/npm-cli.js" "$@"
