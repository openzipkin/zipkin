import {showSpans, hideSpans} from '../../js/component_ui/trace';
import {traceDetailSpan} from './traceTestHelpers';

describe('showSpans', () => {
  it('expands and highlights span to show', () => {
    const span = traceDetailSpan('0000000000000001');
    const spans = {'0000000000000001': span};
    const parents = {'0000000000000001': []};
    const children = {'0000000000000001': []};
    const selected = {0: span};

    showSpans(spans, parents, children, selected);

    span.expanderText.should.contain('-');
    span.expanded.should.equal(true);
    [...span.classes].should.contain('highlight');
  });

  it('sets shown, open parents and children based on selected spans', () => {
    const span1 = traceDetailSpan('0000000000000001');
    const span2 = traceDetailSpan('0000000000000002');
    const span3 = traceDetailSpan('0000000000000003');
    const span4 = traceDetailSpan('0000000000000004');
    const spans = {
      '0000000000000001': span1,
      '0000000000000002': span2,
      '0000000000000003': span3,
      '0000000000000004': span4
    };
    const parents = {
      '0000000000000001': [],
      '0000000000000002': ['0000000000000001'],
      '0000000000000003': ['0000000000000001'],
      '0000000000000004': ['0000000000000003']
    };
    const children = {
      '0000000000000001': ['0000000000000002', '0000000000000003'],
      '0000000000000002': [],
      '0000000000000003': ['0000000000000004'],
      '0000000000000004': []
    };
    const selected = {0: span3};

    showSpans(spans, parents, children, selected);

    span1.shown.should.equal(true); // because a child was selected
    span1.openParents.should.equal(0);
    span1.openChildren.should.equal(1); // because only span3 was selected

    span2.shown.should.equal(false);

    span3.shown.should.equal(true);
    span3.openParents.should.equal(0);
    span3.openChildren.should.equal(0);

    span4.shown.should.equal(true);
    span4.openParents.should.equal(1); // because its parent was selected
    span4.openChildren.should.equal(0);
  });

  it('works when root-most span parent is missing', () => {
    const span = traceDetailSpan('0000000000000001');
    const spans = {'0000000000000001': span};
    const parents = {'0000000000000001': ['0000000000000000']};
    const children = {'0000000000000001': []};
    const selected = {0: span};

    showSpans(spans, parents, children, selected);

    span.shown.should.equal(true);
    span.openParents.should.equal(0);
    span.openChildren.should.equal(0);
  });
});

describe('hideSpans', () => {
  it('collapses and removes highlight', () => {
    const span = traceDetailSpan('0000000000000001');
    const spans = {'0000000000000001': span};
    const parents = {'0000000000000001': []};
    const children = {'0000000000000001': []};
    const selected = {0: span};

    showSpans(spans, parents, children, selected);
    hideSpans(spans, parents, children, selected);

    span.expanderText.should.contain('+');
    span.expanded.should.equal(false);
    [...span.classes].should.not.contain('highlight');
  });

  it('sets hidden, open parents and children based on selected spans', () => {
    const span1 = traceDetailSpan('0000000000000001');
    const span2 = traceDetailSpan('0000000000000002');
    const span3 = traceDetailSpan('0000000000000003');
    const span4 = traceDetailSpan('0000000000000004');
    const spans = {
      '0000000000000001': span1,
      '0000000000000002': span2,
      '0000000000000003': span3,
      '0000000000000004': span4
    };
    const parents = {
      '0000000000000001': [],
      '0000000000000002': ['0000000000000001'],
      '0000000000000003': ['0000000000000001'],
      '0000000000000004': ['0000000000000003']
    };
    const children = {
      '0000000000000001': ['0000000000000002', '0000000000000003'],
      '0000000000000002': [],
      '0000000000000003': ['0000000000000004'],
      '0000000000000004': []
    };
    const selected = {0: span3};

    showSpans(spans, parents, children, selected);
    hideSpans(spans, parents, children, selected);

    span1.hidden.should.equal(true);
    span1.openParents.should.equal(0);
    span1.openChildren.should.equal(0);

    span2.hidden.should.equal(false);

    span3.hidden.should.equal(true);
    span3.openParents.should.equal(0);
    span3.openChildren.should.equal(0);

    span4.hidden.should.equal(true);
    span4.openParents.should.equal(0);
    span4.openChildren.should.equal(0);
  });

  it('doesnt hide parents when childrenOnly', () => {
    const span1 = traceDetailSpan('0000000000000001');
    const span2 = traceDetailSpan('0000000000000002');
    const span3 = traceDetailSpan('0000000000000003');
    const span4 = traceDetailSpan('0000000000000004');
    const spans = {
      '0000000000000001': span1,
      '0000000000000002': span2,
      '0000000000000003': span3,
      '0000000000000004': span4
    };
    const parents = {
      '0000000000000001': [],
      '0000000000000002': ['0000000000000001'],
      '0000000000000003': ['0000000000000001'],
      '0000000000000004': ['0000000000000003']
    };
    const children = {
      '0000000000000001': ['0000000000000002', '0000000000000003'],
      '0000000000000002': [],
      '0000000000000003': ['0000000000000004'],
      '0000000000000004': []
    };
    const selected = {0: span3};

    showSpans(spans, parents, children, selected);
    hideSpans(spans, parents, children, selected, true);

    span1.hidden.should.equal(false);
    span2.hidden.should.equal(false);
    span3.hidden.should.equal(false);
    span4.hidden.should.equal(true);
  });

  it('works when root-most span parent is missing', () => {
    const span = traceDetailSpan('0000000000000001');
    const spans = {'0000000000000001': span};
    const parents = {'0000000000000001': ['0000000000000000']};
    const children = {'0000000000000001': []};
    const selected = {0: span};

    showSpans(spans, parents, children, selected);
    hideSpans(spans, parents, children, selected);

    span.hidden.should.equal(true);
    span.openParents.should.equal(0);
    span.openChildren.should.equal(0);
  });
});
