import {component} from 'flightjs';

export default component(function goToDependency() {
  this.navigateToDependency = function(evt) {
    evt.preventDefault();
    const endTs = document.getElementById('endTs').value;
    const startTs = document.getElementById('startTs').value;
    // eslint-disable-next-line camelcase, no-undef
    window.location.href = `${__webpack_public_path__}dependency?endTs=${endTs}&startTs=${startTs}`;
  };

  this.after('initialize', function() {
    this.on('submit', this.navigateToDependency);
  });
});
