import {
  sortServiceNames
} from '../../js/component_ui/serviceName';

chai.config.truncateThreshold = 0;

describe('sortServiceNames', () => {
  it('should match with the sorted services names', () => {
    const servicesNames = ['Français', 'service_a', 'service_b', 'höger', 'Franca'];
    const sortedServices = ['Franca', 'Français', 'höger', 'service_a', 'service_b'];
    expect(sortServiceNames(servicesNames)).to.have.ordered.members(sortedServices);
  });
});
