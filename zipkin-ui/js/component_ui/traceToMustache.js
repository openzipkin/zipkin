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

export function traceToMustache(tree, logsUrl = undefined) {
  // TODO: this is the max depth of spans, but it probably isn't all that useful vs endpoint depth
  // reason being is that some instrumentation make a lot of intermediate spans. It would probably
  // make sense to align this with service depth as that's what the dependency graph shows. Either
  // that or remote depth.. eg what's the longest chain of remote spans (taking care to ignore
  // redundant instrumentation.
  const depth = tree.maxDepth();

  const v2Trace = tree.traverse();
  const spanCount = v2Trace.length;
  const trace = SPAN_V1.convertTrace(v2Trace);
  const t = traceSummary(v2Trace);

  const traceId = t.traceId;
  const duration = t.duration;
  const serviceNameAndSpanCounts = getServiceNameAndSpanCounts(t.groupedTimestamps);

  const services = serviceNameAndSpanCounts.length || 0;
  const groupByParentId = _(trace).groupBy((s) => s.parentId).value();

  const traceTimestamp = trace[0].timestamp || 0;
  const spanDepths = toSpanDepths(trace);

  const spans = _(getRootSpans(trace)).flatMap(
    (rootSpan) => childrenToList(createSpanTreeEntry(rootSpan, trace))).map((span) => {
      const spanStartTs = span.timestamp || traceTimestamp;
      const spanDepth = spanDepths[span.id] || 1;
      const width = (span.duration || 0) / duration * 100;

      return {
        spanId: span.id,
        parentId: span.parentId || null,
        spanName: span.name,
        serviceNames: span.serviceNames.join(','),
        serviceName: span.serviceName || '',
        duration: span.duration,
        durationStr: mkDurationStr(span.duration),
        left: parseFloat(spanStartTs - traceTimestamp) / parseFloat(duration) * 100,
        width: width < 0.1 ? 0.1 : width,
        depth: (spanDepth + 1) * 5,
        depthClass: (spanDepth - 1) % 6,
        children: (groupByParentId[span.id] || []).map((s) => s.id).join(','),
        annotations: span.annotations.map((a) => ({
          ...a,
          left: (a.timestamp - spanStartTs) / span.duration * 100,
          relativeTime: mkDurationStr(a.timestamp - traceTimestamp),
          width: 8
        })),
        tags: span.tags,
        errorType: getErrorType(span)
      };
    }
  ).value();

  const timeMarkers = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
      .map((p, index) => ({index, time: mkDurationStr(duration * p)}));
  const timeMarkersBackup = timeMarkers;
  const spansBackup = spans;

  return {
    traceId,
    duration: mkDurationStr(duration),
    services,
    depth,
    spanCount,
    serviceNameAndSpanCounts,
    timeMarkers,
    timeMarkersBackup,
    spans,
    spansBackup,
    logsUrl
  };
}
