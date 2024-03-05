/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
// Refer to https://github.com/openzipkin/zipkin-js/blob/master/packages/zipkin/src/model.js

// Same type as Endpoint in the OpenApi/Swagger model https://zipkin.io/zipkin-api/#
type Endpoint = {
  serviceName?: string;
  ipv4?: string;
  ipv6?: string;
  port?: number;
};

export default Endpoint;
