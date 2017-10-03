import {contextRoot} from '../publicPath';
import {component} from 'flightjs';

export default component(function goToDependency() {
  this.navigateToDependency = function(evt) {
    evt.preventDefault();
    const endTs = document.getElementById('endTs').value;
    const startTs = document.getElementById('startTs').value;
    window.location.href = `${contextRoot}dependency?endTs=${endTs}&startTs=${startTs}`;
  };

  this.after('initialize', function() {
    this.on('submit', this.navigateToDependency);
  });
});
