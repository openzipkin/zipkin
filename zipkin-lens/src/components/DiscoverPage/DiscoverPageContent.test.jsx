/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect, afterEach } from 'vitest';
import { fireEvent, cleanup, screen } from '@testing-library/react';
import { createMemoryHistory } from 'history';
import moment from 'moment';
import React from 'react';
import { Router } from 'react-router-dom';

import { renderHook } from '@testing-library/react-hooks/lib/pure';
import { act } from 'react-dom/test-utils';
import DiscoverPageContent, {
  buildApiQuery,
  parseDuration,
  useQueryParams,
} from './DiscoverPageContent';
import render from '../../test/util/render-with-default-settings';

describe('useQueryParams', () => {
  it('should extract criteria from query string', () => {
    const history = createMemoryHistory();
    const wrapper = ({ children }) => {
      return <Router history={history}>{children}</Router>;
    };

    history.push({
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
    });

    const { result } = renderHook(() => useQueryParams(['key3']), { wrapper });

    const expected = [
      { key: 'serviceName', value: 'serviceA' },
      { key: 'spanName', value: 'spanB' },
      { key: 'remoteServiceName', value: 'remoteServiceNameC' },
      { key: 'minDuration', value: '10us' },
      { key: 'maxDuration', value: '100ms' },
      // AnnotationQuery
      { key: 'key3', value: 'value3' },
      { key: 'tagQuery', value: 'key1=value1 and key2' },
    ];

    for (let i = 0; i < expected.length; i += 1) {
      expect(result.current.criteria[i].key).toBe(expected[i].key);
      expect(result.current.criteria[i].value).toBe(expected[i].value);
    }
  });

  it('should extract range lookback from query string', () => {
    const history = createMemoryHistory();
    const wrapper = ({ children }) => {
      return <Router history={history}>{children}</Router>;
    };
    history.push({
      pathname: '/zipkin/',
      search: '?lookback=range&startTs=1588558961791&endTs=1588558961791',
    });

    const { result } = renderHook(() => useQueryParams([]), { wrapper });
    expect(result.current.lookback.type).toBe('range');
    expect(result.current.lookback.startTime.valueOf()).toBe(1588558961791);
    expect(result.current.lookback.endTime.valueOf()).toBe(1588558961791);
  });

  it('should extract fixed lookback from query string', () => {
    const history = createMemoryHistory();
    const wrapper = ({ children }) => {
      return <Router history={history}>{children}</Router>;
    };
    history.push({
      pathname: '/zipkin/',
      search: '?lookback=2h&endTs=1588558961791',
    });

    const { result } = renderHook(() => useQueryParams([]), { wrapper });
    expect(result.current.lookback.type).toBe('fixed');
    expect(result.current.lookback.value).toBe('2h');
    expect(result.current.lookback.endTime.valueOf()).toBe(1588558961791);
  });

  it('should extract millis lookback from query string', () => {
    const history = createMemoryHistory();
    const wrapper = ({ children }) => {
      return <Router history={history}>{children}</Router>;
    };
    history.push({
      pathname: '/zipkin/',
      search: '?lookback=millis&endTs=1588558961791&millis=1234',
    });

    const { result } = renderHook(() => useQueryParams([]), { wrapper });
    expect(result.current.lookback.type).toBe('millis');
    expect(result.current.lookback.value).toBe(1234);
    expect(result.current.lookback.endTime.valueOf()).toBe(1588558961791);
  });

  it('should extract limit from query string', () => {
    const history = createMemoryHistory();
    const wrapper = ({ children }) => {
      return <Router history={history}>{children}</Router>;
    };
    history.push({
      pathname: '/zipkin/',
      search: '?limit=300',
    });

    const { result } = renderHook(() => useQueryParams([]), { wrapper });
    expect(result.current.limit).toBe(300);
  });

  it('should set query string using setQueryParams', () => {
    const history = createMemoryHistory();
    const wrapper = ({ children }) => {
      return <Router history={history}>{children}</Router>;
    };
    history.push({
      pathname: '/zipkin/',
      search: '?limit=300',
    });

    const { result } = renderHook(() => useQueryParams(['key3']), { wrapper });

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
  afterEach(cleanup);

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
