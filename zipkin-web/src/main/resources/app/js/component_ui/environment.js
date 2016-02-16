import flight from 'flight';
export const environment = flight.component(function environment() {
  this.after('initialize', function() {
    this.$node.text(window.config.environment);
  });
});
