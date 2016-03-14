import flight from 'flightjs';
import config from '../config';

export default flight.component(function environmentUI() {
  this.after('initialize', function() {
    this.$node.text(config('environment'));
  });
});
