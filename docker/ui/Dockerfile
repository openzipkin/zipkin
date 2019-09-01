#
# Copyright 2015-2019 The OpenZipkin Authors
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

FROM maven:3-jdk-11-slim

COPY . /code
RUN cd /code && mvn -B --no-transfer-progress package -DskipTests=true -pl zipkin-lens -am

FROM nginx:1.16-alpine
LABEL MAINTAINER Zipkin "https://zipkin.io/"

COPY --from=0 /code/zipkin-lens/target/classes/zipkin-lens /var/www/html/zipkin
RUN mkdir -p /var/tmp/nginx && chown -R nginx:nginx /var/tmp/nginx

# Setup services
COPY docker/ui/nginx.conf /etc/nginx/conf.d/zipkin.conf.template
COPY docker/ui/run.sh /usr/local/bin/nginx.sh

EXPOSE 80

CMD ["/usr/local/bin/nginx.sh"]
