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

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY docker/storage/mysql/*.sh /mysql/
COPY zipkin-storage/mysql-v1/src/main/resources/mysql.sql /mysql/zipkin.sql

FROM alpine:3.12
LABEL MAINTAINER Zipkin "https://zipkin.io/"

WORKDIR /mysql
COPY --from=scratch /mysql/* /mysql/

RUN /mysql/install.sh

CMD /mysql/run.sh

EXPOSE 3306
