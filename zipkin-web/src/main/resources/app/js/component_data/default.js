import {component} from 'flight';
import $ from 'jquery';

export const DefaultData = component(function defaultData() {
  this.after('initialize', function() {
    $.ajax('/modelview' + window.location.pathname + window.location.search, {
      type: "GET",
      dataType: "json",
      context: this,
      success: function(modelview) {
        this.trigger('defaultPageModelView', modelview);
      }
    });
  });
});
