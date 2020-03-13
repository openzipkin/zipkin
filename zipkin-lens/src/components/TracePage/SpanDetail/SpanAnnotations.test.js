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
import { fireEvent, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/extend-expect';

import SpanAnnotations from './SpanAnnotations';

import render from '../../../test/util/render-with-default-settings';

describe('<SpanAnnotations />', () => {
  afterEach(cleanup);

  const span = {
    spanId: '74280ae0c10d8062',
    serviceNames: ['servicea', 'serviceb'],
    annotations: [
      {
        isDerived: true,
        value: 'Server Start',
        timestamp: 1470150004008761,
        endpoint: '192.0.0.0 (serviceb)',
        left: -69.2211679835835,
        relativeTime: '-62307μs',
        width: 8,
      },
      {
        isDerived: true,
        value: 'Client Start',
        timestamp: 1470150004074202,
        endpoint: '127.0.0.0 (servicea)',
        left: 0,
        relativeTime: '3.134ms',
        width: 8,
      },
      {
        isDerived: true,
        value: 'Server Finish',
        timestamp: 1470150004102338,
        endpoint: '192.0.0.0 (serviceb)',
        left: 29.761262547731626,
        relativeTime: '31.270ms',
        width: 8,
      },
      {
        isDerived: true,
        value: 'Client Finish',
        timestamp: 1470150004168741,
        endpoint: '127.0.0.0 (servicea)',
        left: 100,
        relativeTime: '97.673ms',
        width: 8,
      },
    ],
    tags: [],
    errorType: 'none',
    parentId: 'bf396325699c84bf',
    spanName: 'post',
    timestamp: 1470150004074202,
    duration: 94539,
    serviceName: 'serviceb',
    childIds: ['43210ae0c10d1234'],
    depth: 3,
    depthClass: 1,
    width: 58.45916966571439,
    durationStr: '94.539ms',
    left: 1.9379413547038673,
  };

  it('should not render annotation data when mounted', () => {
    const { queryByTestId } = render(<SpanAnnotations span={span} />);
    expect(
      queryByTestId('span-annotations--annotation'),
    ).not.toBeInTheDocument();
  });

  it("should change the toggle button's text when the toggle button is clicked", () => {
    const { getByTestId } = render(<SpanAnnotations span={span} />);
    expect(getByTestId('span-annotations--toggle-button')).toHaveTextContent(
      'show all annotations',
    );
    fireEvent.click(getByTestId('span-annotations--toggle-button'));
    expect(getByTestId('span-annotations--toggle-button')).toHaveTextContent(
      'hide annotations',
    );
    fireEvent.click(getByTestId('span-annotations--toggle-button'));
    expect(getByTestId('span-annotations--toggle-button')).toHaveTextContent(
      'show all annotations',
    );
  });

  it('should show the only one annotation data when an annotation circle is clicked', () => {
    const { getByTestId, getAllByTestId, queryAllByTestId } = render(
      <SpanAnnotations span={span} />,
    );
    fireEvent.click(getByTestId('span-annotations--toggle-button'));
    expect(queryAllByTestId('span-annotations--annotation')).toHaveLength(4);
    expect(getByTestId('span-annotations--toggle-button')).toHaveTextContent(
      'hide annotations',
    );

    // Click an annotation circle.
    fireEvent.click(getAllByTestId('span-annotation-graph--circle')[0]);
    expect(queryAllByTestId('span-annotations--annotation')).toHaveLength(1);
    expect(getByTestId('span-annotations--toggle-button')).toHaveTextContent(
      'show all annotations',
    );
  });

  it('should unselect when the same circle is clicked twice', () => {
    const { getAllByTestId, queryAllByTestId } = render(
      <SpanAnnotations span={span} />,
    );
    fireEvent.click(getAllByTestId('span-annotation-graph--circle')[0]);
    expect(queryAllByTestId('span-annotations--annotation')).toHaveLength(1);
    fireEvent.click(getAllByTestId('span-annotation-graph--circle')[0]);
    expect(queryAllByTestId('span-annotations--annotation')).toHaveLength(0);
  });
});
