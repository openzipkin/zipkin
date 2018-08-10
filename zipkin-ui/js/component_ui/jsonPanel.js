import {component} from 'flightjs';

export default component(function jsonPanel() {
  this.show = function(e, data) {
    this.$node.find('.modal-title').text(data.title);
    this.$node.find('.save').attr('href', data.link);
    this.$node.find('.modal-body pre code').text(JSON.stringify(data.obj, null, 2));
    this.$node.modal('show');
  };
  this.copyToClipboard = function() {
    const traceJson = document.getElementById('trace-json-content');
    function copyListener(e) {
      e.clipboardData.setData('text/plain', traceJson.textContent);
      e.preventDefault();
    }
    document.addEventListener('copy', copyListener);
    document.execCommand('copy');
    document.removeEventListener('copy', copyListener);
  };
  this.after('initialize', function() {
    this.$node.modal('hide');
    this.on(document, 'uiRequestJsonPanel', this.show);
    this.on('#copy2clipboard', 'click', this.copyToClipboard);
  });
});
