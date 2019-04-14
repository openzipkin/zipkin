/* eslint-disable prefer-template */
import {component} from 'flightjs';
import 'chosen-js';
import $ from 'jquery';
import queryString from 'query-string';

export default component(function remoteServiceName() {
  this.updateRemoteServices = function(ev, data) {
    this.render(data.remoteServices);
    this.trigger('chosen:updated');
  };

  this.render = function(remoteServices) {
    this.$node.empty();
    this.$node.append($($.parseHTML('<option value="all">all</option>')));

    const selectedRemoteServiceName = queryString.parse(window.location.search).remoteServiceName;
    $.each(remoteServices, (i, remoteService) => {
      const option = $($.parseHTML('<option/>'));
      option.val(remoteService);
      option.text(remoteService);
      if (remoteService === selectedRemoteServiceName) {
        option.prop('selected', true);
      }
      this.$node.append(option);
    });
  };

  this.after('initialize', function() {
    this.$node.chosen({
      search_contains: true
    });
    this.$node.next('.chosen-container');

    this.on(document, 'dataRemoteServiceNames', this.updateRemoteServices);
  });
});
