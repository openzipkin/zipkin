import React from 'react';
import { shallow } from 'enzyme';

import TraceSummaryBar from './TraceSummaryBar';

describe('<TraceSummaryBar />', () => {
  it('should render the correct width and color', () => {
    const wrapper = shallow(
      <TraceSummaryBar
        width={10}
        infoClass="trace-error-transient"
      >
        <div />
      </TraceSummaryBar>,
    );
    expect(wrapper.find('[data-test="bar-wrapper"]').prop('style')).toEqual({
      width: '10%',
    });
    expect(wrapper.find('[data-test="bar"]').prop('style')).toEqual({
      backgroundColor: '#da8b8b',
    });
  });
});
