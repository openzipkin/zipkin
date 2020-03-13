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

import render from '../../test/util/render-with-default-settings';

import { TracePageImpl } from './TracePage';

jest.mock('./TraceSummary', () => () => (
  <div data-testid="trace-summary">TraceSummary</div>
));
afterAll(() => {
  jest.restoreAllMocks();
});

describe('<TracePage />', () => {
  it('should render an error message when the page is traceViewer and malformed file is loaded', () => {
    const { getByText } = render(
      <TracePageImpl
        isTraceViewerPage
        isMalformedFile
        loadTrace={jest.fn()}
        errorMessage="This is an error message"
      />,
    );

    expect(getByText('This is an error message')).toBeInTheDocument();
  });

  it('should render a loading indicator when the page is trace page and is loading trace', () => {
    const { getByTestId } = render(
      <TracePageImpl
        isTraceViewerPage={false}
        isLoading
        loadTrace={jest.fn()}
      />,
    );
    expect(getByTestId('progress-indicator')).toBeInTheDocument();
  });

  it('should render an info message when the page is traceViewer and the trace has not loaded yet', () => {
    const { getByText } = render(
      <TracePageImpl
        isTraceViewerPage
        isMalformedFile={false}
        loadTrace={jest.fn()}
      />,
    );
    expect(getByText('You need to upload JSON...')).toBeInTheDocument();
  });

  it('should render an error message when the page is trace page and the trace is null', () => {
    const { getByText } = render(
      <TracePageImpl
        isTraceViewerPage={false}
        isLoading={false}
        loadTrace={jest.fn()}
      />,
    );
    expect(getByText('Trace not found')).toBeInTheDocument();
  });

  it('should render TraceSummary when the page is traceViewer and the trace has been loaded', () => {
    const { getByTestId } = render(
      <TracePageImpl
        isTraceViewerPage
        isMalformedFile={false}
        loadTrace={jest.fn()}
        traceSummary={{
          traceId: '1',
          spans: [],
          serviceNameAndSpanCounts: [],
          duration: 1,
          durationStr: '1μs',
          rootSpan: {
            serviceName: 'service-A',
            spanName: 'span-A',
          },
        }}
      />,
    );
    expect(getByTestId('trace-summary')).toBeInTheDocument();
  });

  it('should render TraceSummary when the page is trace page and the trace has been loaded', () => {
    const { getByTestId } = render(
      <TracePageImpl
        isTraceViewerPage={false}
        isLoading={false}
        loadTrace={jest.fn()}
        traceSummary={{
          traceId: '1',
          spans: [],
          serviceNameAndSpanCounts: [],
          duration: 1,
          durationStr: '1μs',
          rootSpan: {
            serviceName: 'service-A',
            spanName: 'span-A',
          },
        }}
      />,
    );
    expect(getByTestId('trace-summary')).toBeInTheDocument();
  });
});
