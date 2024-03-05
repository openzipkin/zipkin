/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import Annotation from './Annotation';
import Endpoint from './Endpoint';

// Refer to https://github.com/openzipkin/zipkin-js/blob/master/packages/zipkin/src/model.js

// Same type as Span in the OpenApi/Swagger model https://zipkin.io/zipkin-api/#
type Span = {
  id: string;
  traceId: string;
  name?: string;
  parentId?: string;
  kind?: 'CLIENT' | 'SERVER' | 'PRODUCER' | 'CONSUMER';
  timestamp?: number;
  duration?: number;
  debug?: boolean;
  shared?: boolean;
  localEndpoint?: Endpoint;
  remoteEndpoint?: Endpoint;
  annotations?: Annotation[];
  tags?: { [key: string]: string };
};

export default Span;
