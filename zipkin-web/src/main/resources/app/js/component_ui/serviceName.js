'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(serviceName);

    function serviceName() {
      this.onChange = function() {
        $.cookie('last-serviceName', this.$node.val());
        this.triggerChange(this.$node.val());
      };

      this.triggerChange = function(name) {
        this.$node.trigger('uiChangeServiceName', name);
      };

      this.after('initialize', function() {
        var lastServiceName = $.cookie('last-serviceName');
        if (this.$node.val() === "" && $.cookie('last-serviceName') !== "") {
          this.$node.find('[value="' + lastServiceName + '"]').attr('selected', 'selected');
          this.triggerChange(lastServiceName);
        }

        this.$node.chosen({search_contains: true});
        this.on('change', this.onChange);
      });
    }

  }
);
