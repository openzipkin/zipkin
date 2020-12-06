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
apk add --update --no-cache mysql=~${MYSQL_VERSION} mysql-client=~${MYSQL_VERSION}
# Fake auth tools install as 10.4.0 install dies otherwise
mkdir -p /auth_pam_tool_dir/auth_pam_tool

mysql_opts="--user=mysql --basedir=${PWD} --datadir=${PWD}/data --tmpdir=/tmp"

# Create the database (stored in the data directory)
ln -s /usr/bin bin
ln -s /usr/share share
mysql_install_db ${mysql_opts} --force

# Enable networking
echo skip-networking=0 >> /etc/my.cnf
echo bind-address=0.0.0.0 >> /etc/my.cnf

# Prevent "Bind on unix socket: No such file or directory"
mkdir -p /run/mysqld/ && chown mysql /run/mysqld/

echo "*** Starting MySQL"
mysqld ${mysql_opts} &
temp_mysql_pid=$!

# Excessively long timeout to avoid having to create an ENV variable, decide its name, etc.
timeout=180
echo "Will wait up to ${timeout} seconds for MySQL to come up before installing Schema"
while [ "$timeout" -gt 0 ] && kill -0 ${temp_mysql_pid} && ! mysql --protocol=socket -uroot -e 'SELECT 1' > /dev/null 2>&1; do
    sleep 1
    timeout=$(($timeout - 1))
done

echo "*** Installing Schema"
mysql --verbose --user=mysql --protocol=socket -uroot <<-'EOF'
USE mysql ;

DELETE FROM mysql.user ;
DROP DATABASE IF EXISTS test ;

CREATE DATABASE zipkin ;

USE zipkin;
SOURCE zipkin-schemas/mysql.sql ;

GRANT ALL PRIVILEGES ON zipkin.* TO zipkin@'%' IDENTIFIED BY 'zipkin' WITH GRANT OPTION ;
FLUSH PRIVILEGES ;
EOF

echo "*** Stopping MySQL"
kill ${temp_mysql_pid}
wait

echo "*** Copying database to /mysql/data"
mkdir /mysql
mv data /mysql/
chown -R mysql /mysql

echo "*** Cleaning Up"
apk del mysql-client
rm bin share
# Remove large binaries
(cd /usr/bin; rm mysql_* aria_* mysqlbinlog myis* test-connect-t mysqlslap innochecksum resolve* my_print_defaults sst_dump)

