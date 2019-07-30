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
import { mount } from 'enzyme';

import { TracesTableRowImpl } from './TracesTableRow';

describe('<TracesTableRow />', () => {
  let wrapper;
  let history;
  let onAddFilter;

  const traceSummary = {
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
  };

  const correctedTraceMap = {
    12345: {
      span: {
        localEndpoint: {
          serviceName: 'service-A',
        },
        name: 'span-A',
      },
    },
  };

  beforeEach(() => {
    history = {
      push: jest.fn(),
    };
    onAddFilter = jest.fn();

    wrapper = mount(
      <TracesTableRowImpl
        traceSummary={traceSummary}
        history={history}
        onAddFilter={onAddFilter}
        correctedTraceMap={correctedTraceMap}
      />,
    );
  });

  it('should render duration bar', () => {
    const item = wrapper.find('[data-test="duration-bar"]').first();
    expect(item.prop('width')).toBe('80%');
  });

  it('should render the service name', () => {
    const item = wrapper.find('[data-test="service-name"]').first();
    expect(item.text()).toBe('service-A');
  });

  it('should render the span name', () => {
    const item = wrapper.find('[data-test="span-name"]').first();
    expect(item.text()).toBe('(span-A)');
  });

  it('should push the history to the individual trace page', () => {
    const item = wrapper.find('[data-test="root"]').first();
    item.simulate('click');
    expect(history.push.mock.calls.length).toBe(1);
    expect(history.push.mock.calls[0][0]).toBe(
      '/zipkin/traces/12345',
    );
  });
});
