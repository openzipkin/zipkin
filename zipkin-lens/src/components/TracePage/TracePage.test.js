/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import { shallow } from 'enzyme';
import CircularProgress from '@material-ui/core/CircularProgress';

import { TracePageImpl } from './TracePage';
import TraceSummary from './TraceSummary';
import MessageBar from './MessageBar';

describe('<TracePage />', () => {
  it('should render an error message when the page is traceViewer and malformed file is loaded', () => {
    const wrapper = shallow(
      <TracePageImpl
        isTraceViewerPage
        isMalformedFile
        loadTrace={jest.fn()}
        errorMessage="This is an error message"
      />,
    );
    expect(wrapper.find(MessageBar).first().prop('message')).toBe(
      'This is an error message',
    );
  });

  it('should render a loading indicator when the page is trace page and is loading trace', () => {
    const wrapper = shallow(
      <TracePageImpl
        isTraceViewerPage={false}
        isLoading
        loadTrace={jest.fn()}
      />,
    );
    expect(wrapper.find(CircularProgress).first().length).toBe(1);
  });

  it('should render an info message when the page is traceViewer and the trace has not loaded yet', () => {
    const wrapper = shallow(
      <TracePageImpl
        isTraceViewerPage
        isMalformedFile={false}
        loadTrace={jest.fn()}
      />,
    );
    expect(wrapper.find(MessageBar).first().prop('message')).toBe(
      'You need to upload JSON...',
    );
  });

  it('should render an error message when the page is trace page and the trace is null', () => {
    const wrapper = shallow(
      <TracePageImpl
        isTraceViewerPage={false}
        isLoading={false}
        loadTrace={jest.fn()}
      />,
    );
    expect(wrapper.find(MessageBar).first().prop('message')).toBe(
      'Trace not found',
    );
  });

  it('should render TraceSummary when the page is traceViewer and the trace has been loaded', () => {
    const wrapper = shallow(
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
    expect(wrapper.find(TraceSummary).first().length).toBe(1);
  });

  it('should render TraceSummary when the page is trace page and the trace has been loaded', () => {
    const wrapper = shallow(
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
    expect(wrapper.find(TraceSummary).first().length).toBe(1);
  });
});
