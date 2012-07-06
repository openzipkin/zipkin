#!/bin/bash
# Copyright 2012 Twitter Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#Usage : find_data.sh hostname input_file_name server_name port_number

HOSTNAME=$1
HDFSFILENAME=$2 
SERVERNAME=$3
PORTNUMBER=$4

#Get the file location for the jar file with all the dependencies and moves it to where the job is being run  
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
DIR="$(dirname "$DIR")"
DIR="$(dirname "$DIR")"
scp $DIR/target/zipkin-hadoop_job_runner-assembly-0.2.0-SNAPSHOT.jar $HOSTNAME:.

#Reads the input into the server
ssh -C $HOSTNAME "java -cp zipkin-hadoop_job_runner-assembly-0.2.0-SNAPSHOT.jar com.twitter.zipkin.hadoop.ProcessPopularKeys "$HDFSFILENAME" "$SERVERNAME" "$PORTNUMBER