/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
// Refer to https://github.com/openzipkin/zipkin-js/blob/master/packages/zipkin/src/model.js

// Same type as Annotation in the OpenApi/Swagger model https://zipkin.io/zipkin-api/#
type Annotation = {
  timestamp: number;
  value: string;
};

export default Annotation;
