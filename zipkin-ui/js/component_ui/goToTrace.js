import {component} from 'flightjs';

export default component(function goToTrace() {
  this.navigateToTrace = function(evt) {
    evt.preventDefault();
    const traceId = document.getElementById('traceIdQuery').value;
    window.location.href = `/zipkin/traces/${traceId}`;
  };

  this.after('initialize', function() {
    this.on('submit', this.navigateToTrace);
  });
});
