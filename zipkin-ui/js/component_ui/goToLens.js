import {component} from 'flightjs';
import Cookies from 'js-cookie';

export default component(function goToLens() {
  this.goToLens = function(evt) {
    evt.preventDefault();
    Cookies.set('lens', 'true');
    window.location.reload(true);
  };

  this.after('initialize', function() {
    this.on('submit', this.goToLens);
  });
});
