/*
 * Copyright 2015-2024 The OpenZipkin Authors
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

/* eslint-disable react/prop-types */

import { fireEvent, screen } from '@testing-library/react';
import { act, renderHook } from '@testing-library/react-hooks';
import moment from 'moment';
import React from 'react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

import DiscoverPageContent, {
  buildApiQuery,
  parseDuration,
  useQueryParams,
} from './DiscoverPageContent';
import render from '../../test/util/render-with-default-settings';

describe('useQueryParams', () => {
  it('should extract criteria from query string', () => {
    const initialEntries = [
      '/zipkin/?serviceName=serviceA&spanName=spanB&remoteServiceName=remoteServiceNameC&minDuration=10us&maxDuration=100ms&annotationQuery=key1%3Dvalue1+and+key2+and+key3%3Dvalue3&limit=10',
    ];
    const wrapper = ({ children }) => (
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    );
    const { result } = renderHook(() => useQueryParams(['key3']), { wrapper });

    const expected = [
      { key: 'serviceName', value: 'serviceA' },
      { key: 'spanName', value: 'spanB' },
      { key: 'remoteServiceName', value: 'remoteServiceNameC' },
      { key: 'minDuration', value: '10us' },
      { key: 'maxDuration', value: '100ms' },
      // Annotation queries
      { key: 'key3', value: 'value3' },
      { key: 'tagQuery', value: 'key1=value1 and key2' },
    ];

    for (let i = 0; i < expected.length; i += 1) {
      expect(result.current.criteria[i].key).toBe(expected[i].key);
      expect(result.current.criteria[i].value).toBe(expected[i].value);
    }
  });

  it('should extract range lookback from query string', () => {
    const initialEntries = [
      '/zipkin/?lookback=range&startTs=1588558961791&endTs=1588558961791',
    ];
    const wrapper = ({ children }) => (
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    );
    const { result } = renderHook(() => useQueryParams([]), { wrapper });

    expect(result.current.lookback.type).toBe('range');
    expect(result.current.lookback.startTime.valueOf()).toBe(1588558961791);
    expect(result.current.lookback.endTime.valueOf()).toBe(1588558961791);
  });

  it('should extract fixed lookback from query string', () => {
    const initialEntries = ['/zipkin/?lookback=2h&endTs=1588558961791']; // 初期URLを設定
    const wrapper = ({ children }) => (
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    );

    const { result } = renderHook(() => useQueryParams([]), { wrapper });

    expect(result.current.lookback.type).toBe('fixed');
    expect(result.current.lookback.value).toBe('2h');
    expect(result.current.lookback.endTime.valueOf()).toBe(1588558961791);
  });

  it('should extract millis lookback from query string', () => {
    const initialEntries = [
      '/zipkin/?lookback=millis&endTs=1588558961791&millis=1234',
    ];
    const wrapper = ({ children }) => (
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    );

    const { result } = renderHook(() => useQueryParams([]), { wrapper });
    expect(result.current.lookback.type).toBe('millis');
    expect(result.current.lookback.value).toBe(1234);
    expect(result.current.lookback.endTime.valueOf()).toBe(1588558961791);
  });

  it('should extract limit from query string', () => {
    const initialEntries = ['/zipkin/?limit=300'];
    const wrapper = ({ children }) => (
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    );

    const { result } = renderHook(() => useQueryParams([]), { wrapper });
    expect(result.current.limit).toBe(300);
  });

  it('should set query string using setQueryParams', async () => {
    const wrapper = ({ children }) => (
      <MemoryRouter initialEntries={['/zipkin/?limit=300']}>
        <Routes>
          <Route path="/zipkin/*" element={children} />
        </Routes>
      </MemoryRouter>
    );

    const { result, waitFor } = renderHook(() => useQueryParams(['key3']), {
      wrapper,
    });

    act(() => {
      result.current.setQueryParams(
        [
          { key: 'serviceName', value: 'serviceA' },
          { key: 'spanName', value: 'spanB' },
          { key: 'remoteServiceName', value: 'remoteServiceNameC' },
          { key: 'minDuration', value: '10us' },
          { key: 'maxDuration', value: '100ms' },
          { key: 'tagQuery', value: 'key1=value1 and key2' },
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

    // URLの更新を待つ
    await waitFor(() => {
      return new URLSearchParams(window.location.search).has('limit');
    });

    // URLパラメータの検証
    const urlParams = new URLSearchParams(window.location.search);
    expect(urlParams.get('serviceName')).toBe('serviceA');
    expect(urlParams.get('spanName')).toBe('spanB');
    expect(urlParams.get('remoteServiceName')).toBe('remoteServiceNameC');
    expect(urlParams.get('minDuration')).toBe('10us');
    expect(urlParams.get('maxDuration')).toBe('100ms');
    expect(urlParams.get('annotationQuery')).toBe(
      'key1=value1 and key2 and key3=value3',
    );
    expect(urlParams.get('lookback')).toBe('2h');
    expect(urlParams.get('endTs')).toBe('1588558961791');
    expect(urlParams.get('limit')).toBe('10');
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
        { key: 'tagQuery', value: 'key1=value1 and key2' },
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
  it('should initialize fixed lookback using config.json', () => {
    const { rerender } = render(<DiscoverPageContent />, {
      uiConfig: {
        defaultLookback: 60 * 1000 * 5, // 5m
      },
    });
    fireEvent.click(screen.getByTestId('settings-button')); // Open settings
    rerender(<DiscoverPageContent />);
    expect(screen.getAllByText('Last 5 minutes').length).toBe(1);
  });

  it('should initialze millis lookback using config.json', () => {
    const { rerender } = render(<DiscoverPageContent />, {
      uiConfig: {
        defaultLookback: 12345,
      },
    });
    fireEvent.click(screen.getByTestId('settings-button')); // Open settings
    rerender(<DiscoverPageContent />);
    expect(screen.getAllByText('12345ms').length).toBe(1);
  });

  it('should initialize the query limit using config.json', () => {
    const { rerender } = render(<DiscoverPageContent />, {
      uiConfig: {
        queryLimit: 30,
      },
    });
    fireEvent.click(screen.getByTestId('settings-button')); // Open settings
    rerender(<DiscoverPageContent />);
    const items = screen.getAllByTestId('query-limit');
    expect(items.length).toBe(1);
    expect(items[0].value).toBe('30');
  });
});
