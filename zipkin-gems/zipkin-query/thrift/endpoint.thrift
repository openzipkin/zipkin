// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

// Author: jsirois

namespace java com.twitter.thrift
namespace rb Twitter.Thrift

/*
 * Represents the status of a service.
 */
enum Status {

  /*
   * The service is dead and can no longer be contacted.
   */
  DEAD = 0,

  /*
   * The service is in the process of starting up for the first time or from a STOPPED state.
   */
  STARTING = 1,

  /*
   * The service is alive and ready to receive requests.
   */
  ALIVE = 2,

  /*
   * The service is in the process of stopping and should no longer be contacted.  In this state
   * well behaved services will typically finish existing requests but accept no new rtequests.
   */
  STOPPING = 3,

  /*
   * The service is stopped and cannot be contacted unless started again.
   */
  STOPPED = 4,

  /*
   * The service is alive but in a potentially bad state.
   */
  WARNING = 5,
}

/*
 * Represents a TCP service network endpoint.
 */
struct Endpoint {

  /*
   * The remote hostname or ip address of the endpoint.
   */
  1: string host

  /*
   * The TCP port the endpoint listens on.
   */
  2: i32 port
}

/*
 * Represents information about the state of a service instance.
 */
struct ServiceInstance {

  /*
   * Represents the primary service interface endpoint.  This is typically a thrift service
   * endpoint.
   */
  1: Endpoint serviceEndpoint

  /*
   * A mapping of any additional interfaces the service exports.  The mapping is from logical
   * interface names to endpoints.  The map may be empty, but a typical additional endpoint mapping
   * would provide the endoint got the "http-admin" debug interface for example.
   *
   * TODO(John Sirois): consider promoting string -> Enum or adding thrift string constants for common
   * service names to help identify common beasts like ostrich-http-admin, ostrich-telnet and
   * process-http-admin but still allow for new experimental interfaces as well without having to
   * change this thift file.
   */
  2: map<string, Endpoint> additionalEndpoints

  /*
   * The status of this service instance.
   */
  3: Status status;
}
