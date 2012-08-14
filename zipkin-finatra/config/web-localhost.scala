/*
* Copyright 2012 Twitter Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import com.twitter.zipkin.config.ZipkinWebConfig
import com.twitter.zipkin.config.zookeeper.ZooKeeperConfig
import java.net.InetSocketAddress

new ZipkinWebConfig {
  // Change the hostname below to allow the Zipkin JS code to talk to the Zipkin API Scala code
  // Suspect this should be marked as a bug really...
  rootUrl = "http://localhost:" + serverPort + "/"

  def zkConfig = new ZooKeeperConfig {
    servers = List("localhost:2181")
  }

}

