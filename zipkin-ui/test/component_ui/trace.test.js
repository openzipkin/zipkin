import {showSpans} from '../../js/component_ui/trace';
import {traceDetailSpan, spanToShow} from './traceTestHelpers';

describe('showSpans', () => {
  it('expands and highlights span to show', () => {
    const spans = {'0000000000000001': traceDetailSpan()};
    const parents = {'0000000000000001': []};
    const children = {'0000000000000001': []};
    const spansToShow = {0: spanToShow('0000000000000001')};

    showSpans(spans, parents, children, spansToShow);

    spansToShow[0].shownText.should.contain('-');
    spansToShow[0].expanded.should.equal(true);
    spansToShow[0].showClasses.should.contain('highlight');
  });

  it('sets open parents and children coherently', () => {
    const spans = {
      '0000000000000001': traceDetailSpan(),
      '0000000000000002': traceDetailSpan(),
      '0000000000000003': traceDetailSpan()
    };
    const parents = {
      '0000000000000001': [],
      '0000000000000002': ['0000000000000001'],
      '0000000000000003': ['0000000000000001']
    };
    const children = {
      '0000000000000001': ['0000000000000002', '0000000000000003'],
      '0000000000000002': [],
      '0000000000000003': []
    };
    const spansToShow = {
      0: spanToShow('0000000000000001'),
      1: spanToShow('0000000000000002'),
      2: spanToShow('0000000000000003'),
    };

    showSpans(spans, parents, children, spansToShow);

    spans['0000000000000001'].shown.should.equal(true);
    spans['0000000000000001'].openParents.should.equal(0);
    spans['0000000000000001'].openChildren.should.equal(2);

    spans['0000000000000002'].shown.should.equal(true);
    spans['0000000000000002'].openParents.should.equal(1);
    spans['0000000000000002'].openChildren.should.equal(0);

    spans['0000000000000003'].shown.should.equal(true);
    spans['0000000000000003'].openParents.should.equal(1);
    spans['0000000000000003'].openChildren.should.equal(0);
  });

  it('works when root-most span parent is missing', () => {
    const spans = {'0000000000000001': traceDetailSpan()};
    const parents = {'0000000000000001': ['0000000000000000']};
    const children = {'0000000000000001': []};
    const spansToShow = {0: spanToShow('0000000000000001')};

    showSpans(spans, parents, children, spansToShow);

    spans['0000000000000001'].shown.should.equal(false);
    spans['0000000000000001'].openParents.should.equal(0);
    spans['0000000000000001'].openChildren.should.equal(0);
  });
});
