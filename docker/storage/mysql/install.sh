#!/bin/sh
#
# Copyright 2015-2020 The OpenZipkin Authors
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

set -eux

echo "*** Installing MySQL"
apk add --update --no-cache mysql mysql-client
# Fake auth tools install as 10.4.0 install dies otherwise
mkdir -p /auth_pam_tool_dir/auth_pam_tool
mysql_install_db --user=mysql --basedir=/usr/ --datadir=/mysql/data --force
mkdir -p /run/mysqld/
chown -R mysql /mysql /run/mysqld/

echo "*** Starting MySQL"
mysqld --user=mysql --basedir=/usr/ --datadir=/mysql/data &

timeout=300
while [[ "$timeout" -gt 0 ]] && ! mysql --user=mysql --protocol=socket -uroot -e 'SELECT 1' >/dev/null 2>/dev/null; do
    echo "Waiting ${timeout} seconds for mysql to come up"
    sleep 2
    timeout=$(($timeout - 2))
done

echo "*** Installing Schema"
mysql --verbose --user=mysql --protocol=socket -uroot <<-EOSQL
USE mysql ;

DELETE FROM mysql.user ;
DROP DATABASE IF EXISTS test ;

CREATE DATABASE zipkin ;

USE zipkin;
SOURCE /mysql/zipkin.sql ;

GRANT ALL PRIVILEGES ON zipkin.* TO zipkin@'%' IDENTIFIED BY 'zipkin' WITH GRANT OPTION ;
FLUSH PRIVILEGES ;
EOSQL

echo "*** Stopping MySQL"
pkill -f mysqld


echo "*** Enabling Networking"
cat >> /etc/my.cnf <<-"EOF"
[mysqld]
skip-networking=0
skip-bind-address
EOF

echo "*** Cleaning Up"
apk del mysql-client --purge
