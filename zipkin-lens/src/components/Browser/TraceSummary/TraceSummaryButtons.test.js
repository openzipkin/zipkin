import React from 'react';
import { shallow } from 'enzyme';

import TraceSummaryButtons from './TraceSummaryButtons';
import * as api from '../../../constants/api';

describe('<TraceSummaryButtons />', () => {
  it('should render the current link', () => {
    const wrapper = shallow(<TraceSummaryButtons traceId="12345" />);
    expect(wrapper.find('[data-test="download"]').prop('href')).toEqual(
      `${api.TRACE}/12345`,
    );
    expect(wrapper.find('[data-test="trace"]').prop('to')).toEqual({
      pathname: '/zipkin/traces/12345',
    });
  });
});
