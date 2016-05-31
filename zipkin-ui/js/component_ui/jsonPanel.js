import {component} from 'flightjs';

export default component(function jsonPanel() {
  this.show = function(e, data) {
    this.$node.find('.modal-title').text(data.title);

    this.$node.find('.save').attr('href', data.link);
    this.$node.find('.modal-body pre').text(JSON.stringify(data.obj, null, 2));
    this.$node.modal('show');
  };

  this.after('initialize', function() {
    this.$node.modal('hide');
    this.on(document, 'uiRequestJsonPanel', this.show);
  });
});
