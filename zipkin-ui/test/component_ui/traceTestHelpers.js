export const frontend = {
  serviceName: 'frontend',
  ipv4: '172.17.0.13'
};
export const backend = {
  serviceName: 'backend',
  ipv4: '172.17.0.9'
};
export const httpTrace = [
  {
    traceId: 'bb1f0e21882325b8',
    parentId: 'bb1f0e21882325b8',
    id: 'c8c50ebd2abc179e',
    kind: 'CLIENT',
    name: 'get',
    timestamp: 1541138169297572,
    duration: 111121,
    localEndpoint: frontend,
    annotations: [
      {value: 'ws', timestamp: 1541138169337695},
      {value: 'wr', timestamp: 1541138169368570}
    ],
    tags: {
      'http.method': 'GET',
      'http.path': '/api'
    }
  },
  {
    traceId: 'bb1f0e21882325b8',
    id: 'bb1f0e21882325b8',
    kind: 'SERVER',
    name: 'get /',
    timestamp: 1541138169255688,
    duration: 168731,
    localEndpoint: frontend,
    remoteEndpoint: {
      ipv4: '110.170.201.178',
      port: 63678
    },
    tags: {
      'http.method': 'GET',
      'http.path': '/',
      'mvc.controller.class': 'Frontend',
      'mvc.controller.method': 'callBackend'
    }
  },
  {
    traceId: 'bb1f0e21882325b8',
    parentId: 'bb1f0e21882325b8',
    id: 'c8c50ebd2abc179e',
    kind: 'SERVER',
    name: 'get /api',
    timestamp: 1541138169377997,
    duration: 26326,
    localEndpoint: backend,
    remoteEndpoint: {
      ipv4: '172.17.0.13',
      port: 63679
    },
    tags: {
      'http.method': 'GET',
      'http.path': '/api',
      'mvc.controller.class': 'Backend',
      'mvc.controller.method': 'printDate'
    },
    shared: true
  }
];

export const errorTrace = [{
  traceId: '1e223ff1f80f1c69',
  id: '1e223ff1f80f1c69',
  timestamp: 1541138169377997,
  duration: 17,
  localEndpoint: backend,
  tags: {error: 'request failed'}
}];

export const minimalTrace = [{
  traceId: '1e223ff1f80f1c69',
  id: '1e223ff1f80f1c69',
  timestamp: 1541138169377997
}];

import skew from '../../testdata/skew';
export const skewedTrace = skew;

import netflix from '../../testdata/netflix';
export const netflixTrace = netflix;

export function traceDetailSpan(id) {
  const expanderText = [];
  const classes = new Set();
  return {
    id,
    inFilters: 0,
    openParents: 0,
    openChildren: 0,
    shown: false,
    show() {this.shown = true; this.hidden = false;},
    hidden: false,
    hide() {this.hidden = true; this.shown = false;},
    expanderText,
    expanded: false,
    $expander: {html: t => expanderText.push(t)},
    classes,
    addClass: c => classes.add(c),
    removeClass: c => classes.delete(c)
  };
}
