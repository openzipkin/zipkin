import {component} from 'flight';
import $ from 'jquery';
import traceToMustache from '../../js/component_ui/traceToMustache';
import chai from 'chai';
import shallowDeepAlmostEqual from 'chai-shallow-deep-almost-equal';
chai.use(shallowDeepAlmostEqual);

export const TraceData = component(function traceData() {
  this.verifyMustache = function(jsModelview) {
    $.ajax('/modelview' + window.location.pathname + window.location.search, {
      type: "GET",
      dataType: "json",
      context: this,
      success: (scalaModelview) => {
        console.log('Scala modelview', scalaModelview);
        console.log('JavaScript modelview', jsModelview);
        chai.expect(jsModelview).to.shallowDeepAlmostEqual(scalaModelview);
        chai.expect(scalaModelview).to.shallowDeepAlmostEqual(jsModelview);
        console.log('Success: they are equal.');
      }
    });
  };

  this.after('initialize', function() {
    $.ajax('/api/v1/trace/' + this.attr.traceId, {
      type: "GET",
      dataType: "json",
      context: this,
      success: (trace) => {
        const modelview = traceToMustache(trace);
        this.trigger('tracePageModelView', modelview);
        this.verifyMustache(modelview);
      }
    });
  });
});
