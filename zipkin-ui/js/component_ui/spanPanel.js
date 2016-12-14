import {component} from 'flightjs';
import $ from 'jquery';
import {Constants} from './traceConstants';

export default component(function spanPanel() {
  this.$annotationTemplate = null;
  this.$binaryAnnotationTemplate = null;
  this.$moreInfoTemplate = null;

  this.show = function(e, span) {
    const self = this;

    this.$node.find('.modal-title').text(
      `${span.serviceName}.${span.spanName}: ${span.durationStr}`);

    this.$node.find('.service-names').text(span.serviceNames);

    const $annoBody = this.$node.find('#annotations tbody').text('');
    $.each((span.annotations || []), (i, anno) => {
      const $row = self.$annotationTemplate.clone();
      if (anno.value === Constants.ERROR) {
        $row.addClass('anno-error-transient');
      }
      $row.find('td').each(function() {
        const $this = $(this);
        const maybeObject = anno[$this.data('key')];
        // In case someone is storing escaped json as an annotation value
        // TODO: this class is not testable at the moment
        $this.text($.type(maybeObject) === 'object' ? JSON.stringify(maybeObject) : maybeObject);
      });
      $annoBody.append($row);
    });

    $annoBody.find('.local-datetime').each(function() {
      const $this = $(this);
      const timestamp = $this.text();
      $this.text((new Date(parseInt(timestamp, 10) / 1000)).toLocaleString());
    });

    const $binAnnoBody = this.$node.find('#binaryAnnotations tbody').text('');
    $.each((span.binaryAnnotations || []), (i, anno) => {
      const $row = self.$binaryAnnotationTemplate.clone();
      if (anno.key === Constants.ERROR) {
        $row.addClass('anno-error-critical');
      }
      $row.find('td').each(function() {
        const $this = $(this);
        const maybeObject = anno[$this.data('key')];
        // In case someone is storing escaped json as binary annotation values
        // TODO: this class is not testable at the moment
        const type = $.type(maybeObject);
        if (type === 'object' || type === 'array') {
          $this.append(`<pre>${JSON.stringify(maybeObject, null, 2)}</pre>`);
        } else {
          $this.text(maybeObject);
        }
      });
      $binAnnoBody.append($row);
    });

    const $moreInfoBody = this.$node.find('#moreInfo tbody').text('');
    const moreInfo = [['traceId', span.traceId],
                       ['spanId', span.id]];
    $.each(moreInfo, (i, pair) => {
      const $row = self.$moreInfoTemplate.clone();
      $row.find('.key').text(pair[0]);
      $row.find('.value').text(pair[1]);
      $moreInfoBody.append($row);
    });

    this.$node.modal('show');
  };

  this.after('initialize', function() {
    this.$node.modal('hide');
    this.$annotationTemplate = this.$node.find('#annotations tbody tr').remove();
    this.$binaryAnnotationTemplate = this.$node.find('#binaryAnnotations tbody tr').remove();
    this.$moreInfoTemplate = this.$node.find('#moreInfo tbody tr').remove();
    this.on(document, 'uiRequestSpanPanel', this.show);
  });
});
