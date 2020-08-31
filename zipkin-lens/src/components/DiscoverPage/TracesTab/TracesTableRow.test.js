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
import React from 'react';
import { cleanup } from '@testing-library/react';

import { TracesTableRowImpl, rootServiceAndSpanName } from './TracesTableRow';
import { SpanNode } from '../../../zipkin/span-node';
import render from '../../../test/util/render-with-default-settings';
import { selectColorByInfoClass } from '../../../colors';

describe('rootServiceAndSpanName', () => {
  it('should return serviceName and spanName of the span', () => {
    const root = new SpanNode({
      traceId: '1',
      id: '1',
      name: 'get',
      localEndpoint: { serviceName: 'frontend' },
    });

    expect(rootServiceAndSpanName(root)).toEqual({
      serviceName: 'frontend',
      spanName: 'get',
    });
  });

  it('should return unknown serviceName when missing localEndpoint', () => {
    const root = new SpanNode({ traceId: '1', id: '1', name: 'get' });

    expect(rootServiceAndSpanName(root)).toEqual({
      serviceName: 'unknown',
      spanName: 'get',
    });
  });

  it('should return unknown serviceName when missing localEndpoint.serviceName', () => {
    const root = new SpanNode({
      traceId: '1',
      id: '1',
      name: 'get',
      localEndpoint: {},
    });

    expect(rootServiceAndSpanName(root)).toEqual({
      serviceName: 'unknown',
      spanName: 'get',
    });
  });

  it('should return unknown spanName when missing span.name', () => {
    const root = new SpanNode({
      traceId: '1',
      id: '1',
      localEndpoint: { serviceName: 'frontend' },
    });

    expect(rootServiceAndSpanName(root)).toEqual({
      serviceName: 'frontend',
      spanName: 'unknown',
    });
  });

  it('should return unknown serviceName and spanName when the span is headless', () => {
    const headless = new SpanNode(); // headless as there's no root span
    const child = new SpanNode({
      traceId: '1',
      parentId: '1',
      id: '2',
      name: 'get',
      localEndpoint: { serviceName: 'frontend' },
    });
    headless.addChild(child);

    expect(rootServiceAndSpanName(headless)).toEqual({
      serviceName: 'unknown',
      spanName: 'unknown',
    });
  });
});

describe('<TracesTableRow />', () => {
  const commonProps = {
    traceSummary: {
      traceId: '12345',
      timestamp: 1,
      duration: 3,
      durationStr: '3μs',
      serviceSummaries: [
        { serviceName: 'service-A', spanCount: 4 },
        { serviceName: 'service-B', spanCount: 8 },
        { serviceName: 'service-C', spanCount: 2 },
      ],
      spanCount: 14,
      width: 80,
    },
    correctedTraceMap: {
      12345: {
        span: {
          localEndpoint: {
            serviceName: 'service-A',
          },
          name: 'span-A',
        },
      },
    },
    onAddFilter: () => {},
  };

  afterEach(cleanup);

  it('should render duration bar', () => {
    const { getByTestId } = render(<TracesTableRowImpl {...commonProps} />);
    const durationBar = getByTestId('duration-bar');
    // width and background-color are changed by their props.
    expect(durationBar).toHaveStyle('width: 80%');
    expect(durationBar).toHaveStyle(
      `background-color: ${selectColorByInfoClass(
        commonProps.traceSummary.infoClass,
      )}`,
    );
  });

  it('should render the service name', () => {
    const { getByTestId } = render(<TracesTableRowImpl {...commonProps} />);
    expect(getByTestId('service-name')).toHaveTextContent('service-A');
  });

  it('should render the span name', () => {
    const { getByTestId } = render(<TracesTableRowImpl {...commonProps} />);
    expect(getByTestId('span-name')).toHaveTextContent('span-A');
  });
});
