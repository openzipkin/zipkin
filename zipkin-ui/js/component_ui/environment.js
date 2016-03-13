import flight from 'flightjs';
export default flight.component(function environmentUI() {
  this.after('initialize', function() {
    this.$node.text(window.config.environment);
  });
});
