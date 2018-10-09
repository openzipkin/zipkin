import {component} from 'flightjs';
import Cookies from 'js-cookie';
import $ from 'jquery';
import queryString from 'query-string';

import 'chosen-js';

  // Sorting based on the localCompare so that sorting can be
  // accomplished for non-ascii (non english) service names.
export function sortServiceNames(serviceNames) {
  if (serviceNames) {
    serviceNames.sort((a, b) =>
       a.localeCompare(b)
    );
  }
  return serviceNames;
}
export default component(function serviceName() {
  this.onChange = function() {
    Cookies.set('last-serviceName', this.$node.val());
    this.triggerChange(this.$node.val());
  };

  this.triggerChange = function(name) {
    this.$node.trigger('uiChangeServiceName', name);
  };

  this.updateServiceNameDropdown = function(ev, data) {
    $('#serviceName').empty();
    this.$node.append($($.parseHTML('<option value="all">all</option>')));
    const services = sortServiceNames(data.names);
    $.each(services, (i, item) => {
      $('<option>').val(item).text(item).appendTo('#serviceName');
    });

    this.$node.find(`[value="${data.lastServiceName}"]`).attr('selected', 'selected');

    this.trigger('chosen:updated');

    // On the first view there won't be a selected or "last" service
    // name.  Instead the first service at the top of the list will be
    // displayed, so load the span names for the top service too.
    if (!data.lastServiceName && services && services.length > 1) {
      this.$node.trigger('uiFirstLoadSpanNames', services[0]);
    }
  };

  this.after('initialize', function() {
    const name = queryString.parse(window.location.search).serviceName
        || Cookies.get('last-serviceName');
    this.triggerChange(name);

    this.$node.chosen({search_contains: true});
    this.$node.next('.chosen-container');

    this.on('change', this.onChange);
    this.on(document, 'dataServiceNames', this.updateServiceNameDropdown);
  });
});
