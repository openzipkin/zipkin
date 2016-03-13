import {component} from 'flightjs';

export default component(function goToDependency() {
  this.navigateToDependency = function(evt) {
    evt.preventDefault();
    var endTs = document.getElementById('endTs').value;
    var startTs = document.getElementById('startTs').value;
    window.location.href='/dependency?endTs=' + endTs + '&startTs=' + startTs;
  };

  this.after('initialize', function() {
    this.on('submit', this.navigateToDependency);
  });
});
