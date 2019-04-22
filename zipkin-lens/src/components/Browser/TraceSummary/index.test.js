import React from 'react';
import { shallow } from 'enzyme';

import TraceSummary from './index';

jest.mock('../../../zipkin', () => ({
  ...(jest.requireActual('../../../zipkin')),
  detailedTraceSummary: () => ({
    traceId: '1',
    spans: [],
    serviceNameAndSpanCounts: [],
    duration: 10,
    durationStr: '10μs',
  }),
}));

describe('<TraceSummary />', () => {
  const defaultProps = {
    traceSummary: {
      traceId: '1',
      duration: 1,
      durationStr: '1μs',
      timestamp: 1,
      serviceSummaries: [{
        serviceName: 'serviceA',
        spanCount: 10,
      }],
      spanCount: 0,
      width: 1,
      infoClass: '',
    },
    skewCorrectedTrace: {},
  };
  it('should change state when clicked', () => {
    const wrapper = shallow(<TraceSummary {...defaultProps} />);
    const event = { stopPropagation: () => {} };
    wrapper.find('[data-test="summary"]').simulate('click', event);
    expect(wrapper.state().isTimelineOpened).toEqual(true);
    wrapper.find('[data-test="summary"]').simulate('click', event);
    expect(wrapper.state().isTimelineOpened).toEqual(false);
  });
});
