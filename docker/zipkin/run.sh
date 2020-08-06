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

if [ -f ".${STORAGE_TYPE}_profile" ]; then
  source ${PWD}/.${STORAGE_TYPE}_profile
fi

# Use main class directly if there are no modules, as it measured 14% faster from JVM running to available
# verses PropertiesLauncher when using Zipkin was based on Spring Boot 2.1
if [[ -z "$MODULE_OPTS" ]]; then
  exec java ${JAVA_OPTS} -cp '.:BOOT-INF/lib/*:BOOT-INF/classes' zipkin.server.ZipkinServer
else
  exec java ${MODULE_OPTS} ${JAVA_OPTS} -cp . org.springframework.boot.loader.PropertiesLauncher
fi
