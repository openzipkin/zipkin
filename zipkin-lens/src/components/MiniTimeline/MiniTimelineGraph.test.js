import React from 'react';
import { shallow } from 'enzyme';

import MiniTimelineGraph from './MiniTimelineGraph';

// TODO: need more tests.
describe('<MiniTimelineGraph />', () => {
  it('should be rendered', () => {
    const wrapper = shallow(
      <MiniTimelineGraph
        spans={[]}
        startTs={0}
        endTs={10}
        duration={10}
        onStartAndEndTsChange={jest.fn()}
        numTimeMarkers={5}
      />,
    );
    expect(wrapper.find('.mini-timeline-graph').length).toBe(1);
  });
});
