/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { ensureV2 } from './trace';
import v2Trace from '../test/data/netflix';

describe('ensureV2', () => {
  it('does not throw on v2 format', () => {
    ensureV2(v2Trace);
  });

  it('should raise error if not a list', () => {
    let error;
    try {
      ensureV2();
    } catch (err) {
      error = err;
    }

    expect(error.message).toEqual('input is not a list');

    try {
      ensureV2({ traceId: 'a', id: 'b' });
    } catch (err) {
      expect(err.message).toEqual(error.message);
    }
  });

  it('should raise error if missing trace ID or span ID', () => {
    let error;
    try {
      ensureV2([{ traceId: 'a' }]);
    } catch (err) {
      error = err;
    }

    expect(error.message).toEqual('List<Span> implies at least traceId and id fields');

    try {
      ensureV2([{ id: 'b' }]);
    } catch (err) {
      expect(err.message).toEqual(error.message);
    }
  });

  it('should raise error if in v1 format', () => {
    let error;
    try {
      ensureV2([{ traceId: 'a', id: 'b', binaryAnnotations: [] }]);
    } catch (err) {
      error = err;
    }

    expect(error.message).toEqual(
      'v1 format is not supported. For help, contact https://gitter.im/openzipkin/zipkin',
    );
  });
});
