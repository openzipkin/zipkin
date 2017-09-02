import {component} from 'flightjs';

export default component(function goToTrace() {
  this.navigateToTrace = function(evt) {
    evt.preventDefault();
    const traceId = document.getElementById('traceIdQuery').value;
    // eslint-disable-next-line camelcase, no-undef
    window.location.href = `${__webpack_public_path__}traces/${traceId}`;
  };

  this.after('initialize', function() {
    this.on('submit', this.navigateToTrace);
  });
});
