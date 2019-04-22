import React from 'react';
import { shallow } from 'enzyme';

import BrowserResults from './BrowserResults';
import TraceSummary from './TraceSummary';
import { sortingMethods } from './sorting';

describe('<BrowserResults />', () => {
  const traceSummaryCommonProps = {
    durationStr: 'μs', // dummy
    timestamp: 1,
    serviceSummaries: [],
    spanCount: 0,
    width: 1,
  };
  const defaultProps = {
    traceSummaries: [
      { traceId: '1', duration: 5, ...traceSummaryCommonProps },
      { traceId: '2', duration: 3, ...traceSummaryCommonProps },
      { traceId: '3', duration: 7, ...traceSummaryCommonProps },
    ],
    sortingMethod: sortingMethods.LONGEST,
    tracesMap: {
      1: { traceId: '1' },
      2: { traceId: '2' },
      3: { traceId: '3' },
    },
  };

  it('should sort trace summaries before rendering', () => {
    const wrapper = shallow(<BrowserResults {...defaultProps} />);
    const traceSummaries = wrapper.find('[data-test="trace-summary-wrapper"]');
    console.log(traceSummaries);
    expect(traceSummaries.at(0).key()).toEqual('3');
    expect(traceSummaries.at(1).key()).toEqual('1');
    expect(traceSummaries.at(2).key()).toEqual('2');
  });

  it('should pass the correct skew corrected trace to TraceSummary', () => {
    const wrapper = shallow(<BrowserResults {...defaultProps} />);
    expect(wrapper.findWhere(n => n.key() === '1').find(TraceSummary)
      .props().skewCorrectedTrace.traceId).toEqual('1');
    expect(wrapper.findWhere(n => n.key() === '2').find(TraceSummary)
      .props().skewCorrectedTrace.traceId).toEqual('2');
    expect(wrapper.findWhere(n => n.key() === '3').find(TraceSummary)
      .props().skewCorrectedTrace.traceId).toEqual('3');
  });
});
