import {component} from 'flightjs';
import Cookies from 'js-cookie';
import $ from 'jquery';
import chosen from 'chosen-npm/public/chosen.jquery.js';
import queryString from 'query-string';

    export default component(function serviceName() {
      this.onChange = function() {
        Cookies.set('last-serviceName', this.$node.val());
        this.triggerChange(this.$node.val());
      };

      this.triggerChange = function(name) {
        this.$node.trigger('uiChangeServiceName', name);
      };

      this.updateServiceNameDropdown = function(ev, data) {
        $('#serviceName').empty()
        $.each(data.serviceNames, function(i, item) {
            $('<option>').val(item).text(item).appendTo('#serviceName');
        });

        this.$node.find('[value="' + data.lastServiceName + '"]').attr('selected', 'selected');

        this.trigger('chosen:updated');
      };

      this.after('initialize', function() {
        const serviceName = queryString.parse(window.location.search).serviceName || Cookies.get('last-serviceName');
        this.triggerChange(serviceName);

        this.$node.chosen({search_contains: true});
        this.on('change', this.onChange);
        this.on(document, 'dataServiceNames', this.updateServiceNameDropdown);
      });
    });
