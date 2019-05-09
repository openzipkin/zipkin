import React from 'react';
import { shallow } from 'enzyme';
import Slider from 'rc-slider';

import MiniTimelineSlider from './MiniTimelineSlider';

const { Range } = Slider;

describe('<MiniTimelineSlider />', () => {
  it('should change isDragging state before changing the range', () => {
    const wrapper = shallow(
      <MiniTimelineSlider
        duration={10}
        startTs={0}
        endTs={10}
        onStartAndEndTsChange={() => {}}
      />,
    );
    wrapper.find(Range).prop('onBeforeChange')();
    expect(wrapper.state('isDragging')).toEqual(true);
  });

  it('should change isDragging state after changing the range', () => {
    const wrapper = shallow(
      <MiniTimelineSlider
        duration={10}
        startTs={0}
        endTs={10}
        onStartAndEndTsChange={() => {}}
      />,
    );
    wrapper.find(Range).prop('onBeforeChange')(); // isDragging === true
    wrapper.find(Range).prop('onAfterChange')([2, 6]);
    expect(wrapper.state('isDragging')).toEqual(false);
  });

  it('should call onStartAndEndTsChange after range change', () => {
    const onStartAndEndTsChange = jest.fn();
    const wrapper = shallow(
      <MiniTimelineSlider
        duration={10}
        startTs={0}
        endTs={10}
        onStartAndEndTsChange={onStartAndEndTsChange}
      />,
    );
    wrapper.find(Range).prop('onAfterChange')([2, 6]);
    expect(onStartAndEndTsChange).toHaveBeenCalledWith(
      2 / 10,
      6 / 10,
    );
  });
});
