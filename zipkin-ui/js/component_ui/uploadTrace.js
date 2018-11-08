import {component} from 'flightjs';
import FullPageSpinnerUI from '../component_ui/fullPageSpinner';
import traceToMustache from '../../js/component_ui/traceToMustache';
import {mergeV2ById} from '../spanCleaner';
import {SPAN_V1} from '../spanConverter';

function ensureV1(trace) {
  if (trace == null || trace.length === 0
          || (trace[0].localEndpoint === undefined && trace[0].remoteEndpoint === undefined)) {
    return trace;
  }

  return SPAN_V1.convertTrace(mergeV2ById(trace));
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
        const rawTrace = JSON.parse(evt.target.result);
        const v1Trace = ensureV1(rawTrace); // We don't need to convert if it is already a v1 trace
        const modelview = traceToMustache(v1Trace);
        model = {modelview, trace: rawTrace};
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
