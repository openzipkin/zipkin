import {component} from 'flightjs';
import FullPageSpinnerUI from '../component_ui/fullPageSpinner';
import traceToMustache from '../../js/component_ui/traceToMustache';
import {SPAN_V1} from '../spanConverter';

function rootToFrontComparator(span1/* , span2*/) {
  return span1.parentId === undefined ? -1 : 0;
}

function sort(trace) {
  if (trace != null) {
    trace.sort(rootToFrontComparator);
  }
}

function ensureV1(trace) {
  if (trace == null || trace.length === 0
          || (trace[0].localEndpoint === undefined && trace[0].remoteEndpoint === undefined)) {
    return trace;
  }

  const newTrace = [];
  for (let i = 0; i < trace.length; i++) {
    newTrace.push(SPAN_V1.convert(trace[i]));
  }

  return newTrace;
}

export default component(function uploadTrace() {
  this.doUpload = function() {
    const files = this.node.files;
    if (files.length === 0) {
      return;
    }

    const reader = new FileReader();
    reader.onload = evt => {
      let model;
      try {
        let trace = JSON.parse(evt.target.result);
        trace = ensureV1(trace);
        sort(trace);

        const modelview = traceToMustache(trace);
        model = {modelview, trace};
      } catch (e) {
        this.trigger('uiServerError',
              {desc: 'Cannot parse file', message: e});
        throw e;
      } finally {
        this.trigger(document, 'uiHideFullPageSpinner');
      }

      this.trigger(document, 'traceViewerPageModelView', model);
    };

    reader.onerror = evt => {
      this.trigger(document, 'uiHideFullPageSpinner');
      this.trigger('uiServerError',
            {desc: 'Cannot load file', message: `${evt.target.error.name}`});
    };

    this.trigger(document, 'uiShowFullPageSpinner');
    setTimeout(() => reader.readAsText(files[0]), 0);
  };

  this.after('initialize', function() {
    this.on('change', this.doUpload);
    FullPageSpinnerUI.teardownAll();
    FullPageSpinnerUI.attachTo('#fullPageSpinner');
  });
});
