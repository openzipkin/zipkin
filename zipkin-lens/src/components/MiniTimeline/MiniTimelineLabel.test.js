import React from 'react';
import { shallow } from 'enzyme';

import MiniTimelineLabel from './MiniTimelineLabel';

describe('<MiniTimelineLabel />', () => {
  it('should set proper positions', () => {
    const wrapper = shallow(<MiniTimelineLabel numTimeMarkers={5} duration={300} />);
    const labelWrappers = wrapper.find('[data-test="label-wrapper"]');
    expect(labelWrappers.at(0).prop('style')).toEqual({ left: '0%' });
    expect(labelWrappers.at(1).prop('style')).toEqual({ left: '25%' });
    expect(labelWrappers.at(2).prop('style')).toEqual({ left: '50%' });
    expect(labelWrappers.at(3).prop('style')).toEqual({ left: '75%' });
    expect(labelWrappers.at(4).prop('style')).toEqual({ left: '100%' });
  });

  it('should set proper modifiers', () => {
    const wrapper = shallow(<MiniTimelineLabel numTimeMarkers={5} duration={300} />);
    const labelWrappers = wrapper.find('[data-test="label"]');
    expect(labelWrappers.at(0).hasClass('mini-timeline-label__label--first'));
    expect(labelWrappers.at(1).hasClass('mini-timeline-label__label--first'));
    expect(labelWrappers.at(2).hasClass('mini-timeline-label__label--first'));
    expect(labelWrappers.at(3).hasClass('mini-timeline-label__label--first'));
    expect(labelWrappers.at(4).hasClass('mini-timeline-label__label--last'));
  });
});
