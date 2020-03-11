/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import moment from 'moment';

import {
  buildCommonQueryParameters,
  buildTracesApiQueryParameters,
  buildDependenciesApiQueryParameters,
  extractConditionsFromQueryParameters,
} from './api';

describe('buildCommonQueryParameters', () => {
  it('should return right query parameters', () => {
    const queryParameters = buildCommonQueryParameters(
      [
        { key: 'serviceName', value: 'serviceA' },
        { key: 'remoteServiceName', value: 'serviceB' },
        { key: 'spanName', value: 'spanA' },
        { key: 'minDuration', value: 10 },
        { key: 'maxDuration', value: 100 },
      ], {
        value: '1h',
      },
      15,
      1547098357716,
    );
    expect(queryParameters).toEqual({
      serviceName: 'serviceA',
      remoteServiceName: 'serviceB',
      spanName: 'spanA',
      minDuration: 10,
      maxDuration: 100,
      lookback: '1h',
      endTs: 1547098357716,
      limit: 15,
    });
  });

  it('should return right query parameters with custom lookback', () => {
    const queryParameters = buildCommonQueryParameters(
      [
        { key: 'serviceName', value: 'serviceA' },
        { key: 'remoteServiceName', value: 'serviceB' },
        { key: 'spanName', value: 'spanA' },
        { key: 'minDuration', value: 10 },
        { key: 'maxDuration', value: 100 },
      ], {
        value: 'custom',
        endTs: 1547098357716,
        startTs: 1547098357701,
      },
      15,
      moment().valueOf(), // This argument is never used when lookback.value is custom.
    );
    expect(queryParameters).toEqual({
      serviceName: 'serviceA',
      remoteServiceName: 'serviceB',
      spanName: 'spanA',
      minDuration: 10,
      maxDuration: 100,
      lookback: 'custom',
      endTs: 1547098357716,
      startTs: 1547098357701,
      limit: 15,
    });
  });

  it('should return right query parameters when the currentTs is not specified', () => {
    const queryParameters = buildCommonQueryParameters(
      [
        { key: 'serviceName', value: 'serviceA' },
        { key: 'remoteServiceName', value: 'serviceB' },
        { key: 'spanName', value: 'spanA' },
        { key: 'minDuration', value: 10 },
        { key: 'maxDuration', value: 100 },
      ], {
        value: '1h',
        endTs: 1547098357716,
      },
      15,
    );
    expect(queryParameters).toEqual({
      serviceName: 'serviceA',
      remoteServiceName: 'serviceB',
      spanName: 'spanA',
      minDuration: 10,
      maxDuration: 100,
      lookback: '1h',
      endTs: 1547098357716,
      limit: 15,
    });
  });

  it('should return right query parameters with a tags', () => {
    const queryParameters = buildCommonQueryParameters(
      [
        { key: 'tags', value: 'key=value' },
      ], {
        value: '1h',
      },
      15,
      1547098357716,
    );
    expect(queryParameters).toEqual({
      tags: 'key=value',
      lookback: '1h',
      endTs: 1547098357716,
      limit: 15,
    });
  });

  it('should return right query parameters with multiple annotationQueries', () => {
    const queryParameters = buildCommonQueryParameters(
      [
        { key: 'tags', value: 'key1=value1' },
        { key: 'tags', value: 'key2' }, // no value
        { key: 'tags', value: 'key3=value3' },
      ], {
        value: '1h',
      },
      15,
      1547098357716,
    );
    expect(queryParameters).toEqual({
      tags: 'key1=value1 and key2 and key3=value3',
      lookback: '1h',
      endTs: 1547098357716,
      limit: 15,
    });
  });
});

describe('buildTracesApiQueryParameters', () => {
  it('should return right query parameters', () => {
    const apiQueryParameters = buildTracesApiQueryParameters(
      [
        { key: 'serviceName', value: 'serviceA' },
        { key: 'remoteServiceName', value: 'serviceB' },
        { key: 'spanName', value: 'spanA' },
        { key: 'minDuration', value: 10 },
        { key: 'maxDuration', value: 100 },
      ], {
        value: 3600000,
      },
      15,
      1547098357716,
    );
    expect(apiQueryParameters).toEqual({
      serviceName: 'serviceA',
      remoteServiceName: 'serviceB',
      spanName: 'spanA',
      minDuration: 10,
      maxDuration: 100,
      endTs: 1547098357716,
      lookback: 3600000,
      limit: 15,
    });
  });

  it('should return right query parameters with custom lookback', () => {
    const apiQueryParameters = buildTracesApiQueryParameters(
      [
        { key: 'serviceName', value: 'serviceA' },
        { key: 'remoteServiceName', value: 'serviceB' },
        { key: 'spanName', value: 'spanA' },
        { key: 'minDuration', value: 10 },
        { key: 'maxDuration', value: 100 },
      ], {
        value: 'custom',
        endTs: 1547098357716,
        startTs: 1547098357710, // lookback == 6
      },
      15,
      moment().valueOf(), // // This argument is never used when lookback.value is custom.
    );
    expect(apiQueryParameters).toEqual({
      serviceName: 'serviceA',
      remoteServiceName: 'serviceB',
      spanName: 'spanA',
      minDuration: 10,
      maxDuration: 100,
      endTs: 1547098357716,
      lookback: 6,
      limit: 15,
    });
  });

  it('should return right query parameters when the currentTs is not specified', () => {
    const apiQueryParameters = buildTracesApiQueryParameters(
      [
        { key: 'serviceName', value: 'serviceA' },
        { key: 'remoteServiceName', value: 'serviceB' },
        { key: 'spanName', value: 'spanA' },
        { key: 'minDuration', value: 10 },
        { key: 'maxDuration', value: 100 },
      ], {
        value: 3600000,
        endTs: 1547098357716,
      },
      15,
    );
    expect(apiQueryParameters).toEqual({
      serviceName: 'serviceA',
      remoteServiceName: 'serviceB',
      spanName: 'spanA',
      minDuration: 10,
      maxDuration: 100,
      endTs: 1547098357716,
      lookback: 3600000,
      limit: 15,
    });
  });

  it('should convert tags to annotationQuery', () => {
    const apiQueryParameters = buildTracesApiQueryParameters(
      [
        {
          key: 'tags', value: 'key1=value1 and key2 and key3=value3',
        },
      ], {
        value: 3600000,
      },
      15,
      1547098357716,
    );
    expect(apiQueryParameters).toEqual({
      annotationQuery: 'key1=value1 and key2 and key3=value3',
      endTs: 1547098357716,
      lookback: 3600000,
      limit: 15,
    });
  });
});

describe('buildDependenciesApiQueryParameters', () => {
  it('should return right query parameters', () => {
    const apiQueryParameters = buildDependenciesApiQueryParameters(
      {
        value: 3600000,
      },
      1547098357716,
    );
    expect(apiQueryParameters).toEqual({
      endTs: 1547098357716,
      lookback: 3600000,
    });
  });

  it('should return right query parameters with custom lookback', () => {
    const apiQueryParameters = buildDependenciesApiQueryParameters(
      {
        value: 'custom',
        endTs: 1547098357716,
        startTs: 1547098357710, // lookback == 6
      },
      moment().valueOf(),
    );
    expect(apiQueryParameters).toEqual({
      endTs: 1547098357716,
      lookback: 6,
    });
  });

  it('should return right query parameters when the currentTs is not specified', () => {
    const apiQueryParameters = buildDependenciesApiQueryParameters(
      {
        value: 3600000,
        endTs: 1547098357716,
      },
    );
    expect(apiQueryParameters).toEqual({
      endTs: 1547098357716,
      lookback: 3600000,
    });
  });
});

describe('extractConditionsFromQueryParameters', () => {
  it('should return right conditions', () => {
    const { conditions } = extractConditionsFromQueryParameters({
      serviceName: 'serviceA',
      remoteServiceName: 'serviceB',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      tags: 'key1=value1 and key2 and key3=value3',
    }, []);
    expect(conditions.sort()).toEqual([
      { key: 'serviceName', value: 'serviceA' },
      { key: 'remoteServiceName', value: 'serviceB' },
      { key: 'spanName', value: 'spanA' },
      { key: 'minDuration', value: 10 },
      { key: 'maxDuration', value: 100 },
      { key: 'tags', value: 'key1=value1' },
      { key: 'tags', value: 'key2' },
      { key: 'tags', value: 'key3=value3' },
    ].sort());
  });

  it('should return right conditions with autocompleteTags', () => {
    const { conditions } = extractConditionsFromQueryParameters({
      serviceName: 'serviceA',
      remoteServiceName: 'serviceB',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      tags: 'key1=value1 and key2 and key3=value3',
      autocompleteTags: 'key4=value4 and key5=value5',
    }, []);
    expect(conditions.sort()).toEqual([
      { key: 'serviceName', value: 'serviceA' },
      { key: 'remoteServiceName', value: 'serviceB' },
      { key: 'spanName', value: 'spanA' },
      { key: 'minDuration', value: 10 },
      { key: 'maxDuration', value: 100 },
      { key: 'tags', value: 'key1=value1' },
      { key: 'tags', value: 'key2' },
      { key: 'tags', value: 'key3=value3' },
      { key: 'key4', value: 'value4' },
      { key: 'key5', value: 'value5' },
    ].sort());
  });

  it('should return the right limit condition', () => {
    const { limitCondition } = extractConditionsFromQueryParameters({ limit: '15' });
    expect(limitCondition).toEqual(15);
  });

  it('should return the right lookback condition', () => {
    const { lookbackCondition } = extractConditionsFromQueryParameters({
      lookback: 3600000,
      endTs: '1547098357716',
    });
    expect(lookbackCondition).toEqual({
      value: 3600000,
      endTs: 1547098357716,
    });
  });

  it('should return the right custom lookback condition', () => {
    const { lookbackCondition } = extractConditionsFromQueryParameters({
      lookback: 'custom',
      endTs: '1547098357716',
      startTs: '1547098357710',
    });
    expect(lookbackCondition).toEqual({
      value: 'custom',
      endTs: 1547098357716,
      startTs: 1547098357710,
    });
  });
});
