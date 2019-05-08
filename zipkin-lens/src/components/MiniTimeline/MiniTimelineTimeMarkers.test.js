import React from 'react';
import { shallow } from 'enzyme';

import MiniTimelineTimeMarkers from './MiniTimelineTimeMarkers';

describe('<MiniTimelineTimeMarkers />', () => {
  it('should set proper positions', () => {
    const wrapper = shallow(<MiniTimelineTimeMarkers height={75} numTimeMarkers={5} />);
    const timeMarkers = wrapper.find('line');
    expect(timeMarkers.at(0).prop('x1')).toEqual('25%');
    expect(timeMarkers.at(1).prop('x1')).toEqual('50%');
    expect(timeMarkers.at(2).prop('x1')).toEqual('75%');
  });
});
