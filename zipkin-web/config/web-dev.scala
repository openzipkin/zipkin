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
import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.zipkin.builder.{QueryClient, WebBuilder}
import java.net.{InetSocketAddress, InetAddress}

// We'd rather use InetAddress.getLoopbackAddress than 127.0.0.1 but it's JDK
// 1.7+. Previously we used InetAddress.getLocalHost, but that doesn't let us
// do SSH port forwarding.
val queryClient = QueryClient.static(new InetSocketAddress("127.0.0.1", 9411)) map {
  _.tracer(ZipkinTracer.mk())
}
WebBuilder("http://localhost:8080/", queryClient)
