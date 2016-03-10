import flight from 'flightjs';
const EnvironmentUI = flight.component(function environment() {
  this.after('initialize', function() {
    this.$node.text(window.config.environment);
  });
});
export default EnvironmentUI;
