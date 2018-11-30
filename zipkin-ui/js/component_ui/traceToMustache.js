import _ from 'lodash';
import {
  traceSummary,
  getServiceNameAndSpanCounts,
  mkDurationStr
} from './traceSummary';
import {SPAN_V1} from '../spanConverter';

export function getRootSpans(spans) {
  const ids = spans.map((s) => s.id);
  return spans.filter((s) => ids.indexOf(s.parentId) === -1);
}

function compareSpan(s1, s2) {
  return (s1.timestamp || 0) - (s2.timestamp || 0);
}

function childrenToList(entry) {
  const deepChildren = _(entry.children || [])
    .sort((e1, e2) => compareSpan(e1.span, e2.span))
    .flatMap(childrenToList).value();
  return [entry.span, ...deepChildren];
}

function createSpanTreeEntry(span, trace, indexByParentId = null) {
  const idx = indexByParentId != null ? indexByParentId : _(trace)
        .filter((s) => s.parentId != null)
        .groupBy((s) => s.parentId)
        .value();

  return {
    span,
    children: (idx[span.id] || [])
      .map((s) => createSpanTreeEntry(s, trace, idx))
  };
}

function recursiveGetRootMostSpan(idSpan, prevSpan) {
  if (prevSpan.parentId && idSpan[prevSpan.parentId]) {
    return recursiveGetRootMostSpan(idSpan, idSpan[prevSpan.parentId]);
  } else {
    return prevSpan;
  }
}

function getRootMostSpan(spans) {
  const firstWithoutParent = _(spans).find((s) => !s.parentId);
  if (firstWithoutParent) {
    return firstWithoutParent;
  } else {
    const idToSpanMap = _(spans).groupBy((s) => s.id).mapValues(([s]) => s);
    return recursiveGetRootMostSpan(idToSpanMap, spans[0]);
  }
}

function treeDepths(entry, startDepth) {
  const initial = {};
  initial[entry.span.id] = startDepth;
  if (entry.children.length === 0) {
    return initial;
  }
  return _(entry.children || []).reduce((prevMap, child) => {
    const childDepths = treeDepths(child, startDepth + 1);
    const newCombined = {
      ...prevMap,
      ...childDepths
    };
    return newCombined;
  }, initial);
}

function toSpanDepths(spans) {
  const rootMost = getRootMostSpan(spans);
  const entry = createSpanTreeEntry(rootMost, spans);
  return treeDepths(entry, 1);
}

function getErrorType(span) {
  if (span.tags.findIndex(b => b.key === 'error') !== -1) {
    return 'critical';
  } else if (span.annotations.findIndex(a => a.value === 'error') !== -1) { // TODO: indexOf!
    return 'transient';
  } else {
    return 'none';
  }
}

export function traceToMustache(tree, logsUrl) {
  // TODO: this is the max depth of spans, but it probably isn't all that useful vs endpoint depth
  // reason being is that some instrumentation make a lot of intermediate spans. It would probably
  // make sense to align this with service depth as that's what the dependency graph shows. Either
  // that or remote depth.. eg what's the longest chain of remote spans (taking care to ignore
  // redundant instrumentation.
  const depth = tree.maxDepth();

  const v2Trace = tree.traverse();
  const t = traceSummary(v2Trace);
  const spanCount = t.spanCount;
  const traceId = t.traceId;
  const traceDuration = t.duration || 0;
  const serviceNameAndSpanCounts = getServiceNameAndSpanCounts(t.groupedTimestamps);

  const trace = SPAN_V1.convertTrace(v2Trace);
  const groupByParentId = _(trace).groupBy((s) => s.parentId).value();
  const traceTimestamp = trace[0].timestamp || 0;
  const spanDepths = toSpanDepths(trace);

  const spans = _(getRootSpans(trace)).flatMap(
    (rootSpan) => childrenToList(createSpanTreeEntry(rootSpan, trace))).map((span) => {
      const spanStartTs = span.timestamp || traceTimestamp;
      const spanDepth = spanDepths[span.id] || 1;
      const spanDuration = span.duration || 0;
      const children = (groupByParentId[span.id] || []).map((s) => s.id);

      const res = {
        spanId: span.id,
        depth: (spanDepth + 1) * 5,
        depthClass: (spanDepth - 1) % 6,
        annotations: span.annotations.map((a) => ({
          ...a,
          left: spanDuration ? (a.timestamp - spanStartTs) / spanDuration * 100 : 0,
          relativeTime: mkDurationStr(a.timestamp - traceTimestamp),
          width: 8
        })),
        tags: span.tags,
        errorType: getErrorType(span)
      };

      // Optionally add fields instead of defaulting to empty string
      if (span.name) res.spanName = span.name;
      if (spanDuration) {
        const width = traceDuration ? spanDuration / traceDuration * 100 : 0;
        res.width = width < 0.1 ? 0.1 : width;
        res.left = parseFloat(spanStartTs - traceTimestamp) / parseFloat(traceDuration) * 100;
        res.duration = spanDuration; // used in zoom
        res.durationStr = mkDurationStr(spanDuration); // bubble over the span in trace view
      } else {
        res.left = 0;
        res.width = 0.1;
      }
      if (span.serviceName) res.serviceName = span.serviceName;
      if (span.serviceNames.length !== 0) res.serviceNames = span.serviceNames.join(',');
      if (span.parentId) res.parentId = span.parentId;
      if (children.length !== 0) res.children = children.join(','); // used for expand and collapse
      return res;
    }
  ).value();

  const res = {
    traceId,
    depth,
    spanCount,
    serviceNameAndSpanCounts,
    spans
  };

  // the zoom feature needs backups and timeMarkers regardless of if there is a trace duration
  res.spansBackup = res.spans;
  res.timeMarkers = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
    .map((p, index) => ({index, time: mkDurationStr(traceDuration * p)}));
  res.timeMarkersBackup = res.timeMarkers;

  if (traceDuration) res.durationStr = mkDurationStr(traceDuration);
  if (logsUrl) res.logsUrl = logsUrl;

  return res;
}
