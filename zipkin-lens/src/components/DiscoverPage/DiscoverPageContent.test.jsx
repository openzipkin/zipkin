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

import { act, renderHook } from '@testing-library/react-hooks';
import { createMemoryHistory } from 'history';
import moment from 'moment';
import React from 'react';

import DiscoverPageContent, {
  buildApiQuery,
  parseDuration,
  useQueryParams,
} from './DiscoverPageContent';
import render from '../../test/util/render-with-default-settings';

describe('useQueryParams', () => {
  it('should extract criteria from query string', () => {
    const { result } = renderHook(() =>
      useQueryParams(
        {},
        {
          pathname: '/zipkin/',
          // serviceName: serviceA
          // spanName: spanB
          // remoteServiceName: remoteServiceNameC
          // minDuration: 10us
          // maxDuration: 100ms
          // annotationQuery:
          //   key1: value1
          //   key2
          //   key3: value3
          search:
            '?serviceName=serviceA&spanName=spanB&remoteServiceName=remoteServiceNameC&minDuration=10us&maxDuration=100ms&annotationQuery=key1%3Dvalue1+and+key2+and+key3%3Dvalue3&limit=10',
        },
        ['key3'],
      ),
    );
    expect(result.current.criteria).toEqual([
      { key: 'serviceName', value: 'serviceA' },
      { key: 'spanName', value: 'spanB' },
      { key: 'remoteServiceName', value: 'remoteServiceNameC' },
      { key: 'minDuration', value: '10us' },
      { key: 'maxDuration', value: '100ms' },
      // AnnotationQuery
      { key: 'key3', value: 'value3' },
      { key: 'tags', value: 'key1=value1 and key2' },
    ]);
  });

  it('should extract custom lookback from query string', () => {
    const { result } = renderHook(() =>
      useQueryParams(
        {},
        {
          pathname: '/zipkin/',
          search: '?lookback=custom&startTs=1588558961791&endTs=1588558961791',
        },
      ),
    );
    expect(result.current.lookback.type).toBe('custom');
    expect(result.current.lookback.startTime.valueOf()).toBe(1588558961791);
    expect(result.current.lookback.endTime.valueOf()).toBe(1588558961791);
  });

  it('should extract fixed lookback from query string', () => {
    const { result } = renderHook(() =>
      useQueryParams(
        {},
        {
          pathname: '/zipkin/',
          search: '?lookback=2h&endTs=1588558961791',
        },
      ),
    );
    expect(result.current.lookback.type).toBe('fixed');
    expect(result.current.lookback.value).toBe('2h');
    expect(result.current.lookback.endTime.valueOf()).toBe(1588558961791);
  });

  it('should extract limit from query string', () => {
    const { result } = renderHook(() =>
      useQueryParams(
        {},
        {
          pathname: '/zipkin/',
          search: '?limit=300',
        },
      ),
    );
    expect(result.current.limit).toBe(300);
  });

  it('should set query string using setQueryParams', () => {
    const history = createMemoryHistory();
    const { result } = renderHook(() =>
      useQueryParams(history, history.location, ['key3']),
    );

    act(() => {
      result.current.setQueryParams(
        [
          { key: 'serviceName', value: 'serviceA' },
          { key: 'spanName', value: 'spanB' },
          { key: 'remoteServiceName', value: 'remoteServiceNameC' },
          // Durations will NOT converted to microsecond values.
          { key: 'minDuration', value: '10us' },
          { key: 'maxDuration', value: '100ms' },
          // AnnotationQuery
          { key: 'tags', value: 'key1=value1 and key2' },
          { key: 'key3', value: 'value3' },
        ],
        {
          type: 'fixed',
          endTime: moment(1588558961791),
          value: '2h',
        },
        10,
      );
    });
    expect(history.location.search).toBe(
      '?serviceName=serviceA&spanName=spanB&remoteServiceName=remoteServiceNameC&minDuration=10us&maxDuration=100ms&annotationQuery=key1%3Dvalue1+and+key2+and+key3%3Dvalue3&lookback=2h&endTs=1588558961791&limit=10',
    );
  });
});

it('parseDuration', () => {
  [
    { in: '35', out: 35 },
    { in: '35us', out: 35 },
    { in: '35ms', out: 35 * 1000 },
    { in: '35s', out: 35 * 1000 * 1000 },
  ].forEach((e) => {
    expect(parseDuration(e.in)).toBe(e.out);
  });
});

describe('buildApiQuery', () => {
  it('should build API Query', () => {
    const params = buildApiQuery(
      [
        { key: 'serviceName', value: 'serviceA' },
        { key: 'spanName', value: 'spanB' },
        { key: 'remoteServiceName', value: 'remoteServiceNameC' },
        // Durations will converted to microsecond values.
        { key: 'minDuration', value: '10us' },
        { key: 'maxDuration', value: '100ms' },
        // AnnotationQuery
        { key: 'tags', value: 'key1=value1 and key2' },
        { key: 'key3', value: 'value3' },
      ],
      {
        type: 'fixed',
        endTime: moment(1588558961791),
        value: '2h',
      },
      30,
      ['key3'],
    );
    expect(params.serviceName).toBe('serviceA');
    expect(params.spanName).toBe('spanB');
    expect(params.remoteServiceName).toBe('remoteServiceNameC');
    expect(params.minDuration).toBe('10');
    expect(params.maxDuration).toBe('100000');
    expect(params.annotationQuery).toBe('key1=value1 and key2 and key3=value3');
    expect(params.lookback).toBe('7200000');
    expect(params.endTs).toBe('1588558961791');
    expect(params.limit).toBe('30');
  });
});

describe('<DiscoverPageContent />', () => {
  it('should initialize the lookback using config.json', () => {
    const { getAllByText } = render(<DiscoverPageContent />, {
      uiConfig: {
        defaultLookback: 60 * 1000 * 5, // 5m
      },
    });
    expect(getAllByText('Last 5 minutes').length).toBe(1);
  });

  it('should initialize the query limit using config.json', () => {
    const { getAllByTestId } = render(<DiscoverPageContent />, {
      uiConfig: {
        queryLimit: 30,
      },
    });
    const items = getAllByTestId('query-limit');
    expect(items.length).toBe(1);
    expect(items[0].value).toBe('30');
  });
});
