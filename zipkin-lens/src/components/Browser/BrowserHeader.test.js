import React from 'react';
import { shallow } from 'enzyme';

import BrowserHeader from './BrowserHeader';
import { sortingMethods } from './sorting';

describe('<BrowserHeader />', () => {
  it('should render correct the number of results', () => {
    const wrapper = shallow(
      <BrowserHeader
        numTraces={35}
        sortingMethod={sortingMethods.LONGEST}
        onChange={() => {}}
      />,
    );
    const totalResultsText = wrapper.find('[data-test="total-results"]').text();
    expect(totalResultsText).toEqual('35 results');
  });
});
