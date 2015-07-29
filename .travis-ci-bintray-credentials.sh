#!/bin/bash
mkdir ~/.bintray/
FILE=$HOME/.bintray/.credentials
cat <<EOF >$FILE
realm = Bintray API Realm
host = api.bintray.com
user = $BINTRAY_USER
password = $BINTRAY_API_KEY
EOF
echo $BINTRAY_USER
echo "Created ~/.bintray/.credentials file: Here it is: "
ls -la $FILE
