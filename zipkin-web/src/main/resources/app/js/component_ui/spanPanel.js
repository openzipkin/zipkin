'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(spanPanel);

    function spanPanel() {
      this.$annotationTemplate = null;
      this.$binaryAnnotationTemplate = null;

      this.show = function(e, span) {
        var self = this;

        this.$node.find('.modal-title').text(
          span.serviceNames + '.' + span.spanName + ': ' + span.durationStr);

        var $annoBody = this.$node.find('#annotations tbody').text('');
        $.each(span.annotations, function(i, anno) {
          var $row = self.$annotationTemplate.clone();
          $row.find('td').each(function() {
            var $this = $(this);
            $this.text(anno[$this.data('key')]);
          });
          $annoBody.append($row);
        });

        $annoBody.find(".local-datetime").each(function() {
          var $this = $(this);
          var timestamp = $this.text();
          $this.text((new Date(parseInt(timestamp) / 1000)).toLocaleString());
        });

        var $binAnnoBody = this.$node.find('#binaryAnnotations tbody').text('');
        $.each(span.binaryAnnotations, function(i, anno) {
          var $row = self.$binaryAnnotationTemplate.clone();
          $row.find('td').each(function() {
            var $this = $(this);
            $this.text(anno[$this.data('key')]);
          });
          $binAnnoBody.append($row);
        });

        this.$node.modal('show');
      };

      this.after('initialize', function() {
        this.$node.modal('hide');
        this.$annotationTemplate = this.$node.find('#annotations tbody tr').remove();
        this.$binaryAnnotationTemplate = this.$node.find('#binaryAnnotations tbody tr').remove();
        this.on(document, 'uiRequestSpanPanel', this.show);
      });
    }

  }
);
