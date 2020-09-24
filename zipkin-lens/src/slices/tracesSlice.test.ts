/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import configureStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import { loadTrace, searchTraces, TracesState } from './tracesSlice';
import * as api from '../constants/api';
import TraceSummary from '../models/TraceSummary';
import Span from '../models/Span';
import AdjustedTrace from '../models/AdjustedTrace';

const {
  treeCorrectedForClockSkew,
  detailedTraceSummary: buildDetailedTraceSummary,
} = require('../zipkin');

const frontend = {
  serviceName: 'frontend',
  ipv4: '172.17.0.13',
};

const backend = {
  serviceName: 'backend',
  ipv4: '172.17.0.9',
};

const httpTrace: Span[] = [
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
      { value: 'ws', timestamp: 1541138169337695 },
      { value: 'wr', timestamp: 1541138169368570 },
    ],
    tags: {
      'http.method': 'GET',
      'http.path': '/api',
    },
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
      port: 63678,
    },
    tags: {
      'http.method': 'GET',
      'http.path': '/',
      'mvc.controller.class': 'Frontend',
      'mvc.controller.method': 'callBackend',
    },
  },
  {
    traceId: 'bb1f0e21882325b8',
    parentId: 'bb1f0e21882325b8',
    id: 'c8c50ebd2abc179e',
    kind: 'SERVER',
    name: 'get /api',
    timestamp: 1541138169377997, // this is actually skewed right, but we can't correct it
    duration: 26326,
    localEndpoint: backend,
    remoteEndpoint: {
      ipv4: '172.17.0.13',
      port: 63679,
    },
    tags: {
      'http.method': 'GET',
      'http.path': '/api',
      'mvc.controller.class': 'Backend',
      'mvc.controller.method': 'printDate',
    },
    shared: true,
  },
];

describe('tracesSlice', () => {
  describe('searchTraces', () => {
    afterEach(() => fetchMock.reset());

    it('should return the existing state, if the search criteria have not been changed', () => {
      const mockStore = configureStore([thunk]);

      const traceSummaries: TraceSummary[] = [
        {
          traceId: '12345',
          timestamp: 123456789,
          duration: 1234,
          serviceSummaries: [],
          spanCount: 10,
          width: 10,
          root: {
            serviceName: 'Example Service',
            spanName: 'Example Span',
          },
        },
      ];

      const query = {
        serviceName: 'Example Service',
        spanName: 'Example Span',
      };
      const queryStr = new URLSearchParams(query).toString();

      const initialState: { traces: TracesState } = {
        traces: {
          traces: {},
          search: {
            isLoading: false,
            error: undefined,
            prevQuery: queryStr,
            traceSummaries,
          },
        },
      };

      const store = mockStore(initialState);

      return store.dispatch(searchTraces(query)).then(() => {
        expect(store.getActions().length).toBe(2);
        expect(store.getActions()[0].type).toBe('traces/search/pending');
        expect(store.getActions()[1].type).toBe('traces/search/fulfilled');
        const [, { payload }] = store.getActions();
        expect(payload.traceSummaries).toBe(traceSummaries);
        expect(payload.query).toBe(queryStr);
      });
    });

    it('should fetch new traces, if the search criteria have been changed', () => {
      const mockStore = configureStore([thunk]);

      const prevTraceSummaries: TraceSummary[] = [
        {
          traceId: '12345',
          timestamp: 123456789,
          duration: 1234,
          serviceSummaries: [],
          spanCount: 10,
          width: 10,
          root: {
            serviceName: 'Previous Example Service',
            spanName: 'Previous Example Span',
          },
        },
      ];

      const prevQuery = {
        serviceName: 'Previous Example Service',
        spanName: 'Previous Example Span',
      };
      const prevQueryStr = new URLSearchParams(prevQuery).toString();

      const initialState: { traces: TracesState } = {
        traces: {
          traces: {},
          search: {
            isLoading: false,
            error: undefined,
            prevQuery: prevQueryStr,
            traceSummaries: prevTraceSummaries,
          },
        },
      };

      fetchMock.get(`${api.TRACES}?serviceName=Example+Service`, {
        status: 200,
        body: [httpTrace],
      });

      const store = mockStore(initialState);

      const newQuery = {
        serviceName: 'Example Service',
      };
      const newQueryStr = new URLSearchParams(newQuery).toString();

      return store.dispatch(searchTraces(newQuery)).then(() => {
        expect(store.getActions().length).toBe(2);
        expect(store.getActions()[0].type).toBe('traces/search/pending');
        expect(store.getActions()[1].type).toBe('traces/search/fulfilled');
        const [, { payload }] = store.getActions();
        expect(payload.traceSummaries).not.toBe(prevTraceSummaries);
        expect(payload.query).toBe(newQueryStr);
      });
    });
  });

  describe('loadTrace', () => {
    it('should return the existing state, if the trace already exists', () => {
      const mockStore = configureStore([thunk]);

      const skewCorrectedTrace = treeCorrectedForClockSkew(httpTrace);
      const adjustedTrace: AdjustedTrace = buildDetailedTraceSummary(
        skewCorrectedTrace,
      );

      const initialState: { traces: TracesState } = {
        traces: {
          traces: {
            bb1f0e21882325b8: {
              isLoading: false,
              error: undefined,
              rawTrace: httpTrace,
              adjustedTrace,
              skewCorrectedTrace,
            },
          },
          search: {
            isLoading: false,
            error: undefined,
            prevQuery: undefined,
            traceSummaries: [],
          },
        },
      };

      const store = mockStore(initialState);

      return store.dispatch(loadTrace('bb1f0e21882325b8')).then(() => {
        expect(store.getActions().length).toBe(2);
        expect(store.getActions()[0].type).toBe('traces/load/pending');
        expect(store.getActions()[1].type).toBe('traces/load/fulfilled');
        const [, { payload }] = store.getActions();
        expect(payload.rawTrace).toBe(httpTrace);
        expect(payload.skewCorrectedTrace).toBe(skewCorrectedTrace);
        expect(payload.adjustedTrace).toBe(adjustedTrace);
      });
    });

    it("should calculate adjustedTrace by buildDetailedTraceSummary, if the trace's skewCorrectedTrace already exists", () => {
      const mockStore = configureStore([thunk]);

      const skewCorrectedTrace = treeCorrectedForClockSkew(httpTrace);
      const adjustedTrace: AdjustedTrace = buildDetailedTraceSummary(
        skewCorrectedTrace,
      );

      const initialState: { traces: TracesState } = {
        traces: {
          traces: {
            bb1f0e21882325b8: {
              isLoading: false,
              error: undefined,
              rawTrace: httpTrace,
              adjustedTrace: undefined,
              skewCorrectedTrace,
            },
          },
          search: {
            isLoading: false,
            error: undefined,
            prevQuery: undefined,
            traceSummaries: [],
          },
        },
      };

      const store = mockStore(initialState);

      return store.dispatch(loadTrace('bb1f0e21882325b8')).then(() => {
        expect(store.getActions().length).toBe(2);
        expect(store.getActions()[0].type).toBe('traces/load/pending');
        expect(store.getActions()[1].type).toBe('traces/load/fulfilled');
        const [, { payload }] = store.getActions();
        expect(payload.rawTrace).toBe(httpTrace);
        expect(payload.skewCorrectedTrace).toBe(skewCorrectedTrace);
        expect(payload.adjustedTrace).toEqual(adjustedTrace);
      });
    });

    it('should fetch the trace, if the trace data does not exist', () => {
      const mockStore = configureStore([thunk]);

      const skewCorrectedTrace = treeCorrectedForClockSkew(httpTrace);
      const adjustedTrace: AdjustedTrace = buildDetailedTraceSummary(
        skewCorrectedTrace,
      );

      const initialState: { traces: TracesState } = {
        traces: {
          traces: {},
          search: {
            isLoading: false,
            error: undefined,
            prevQuery: undefined,
            traceSummaries: [],
          },
        },
      };

      fetchMock.get(`${api.TRACE}/bb1f0e21882325b8`, {
        status: 200,
        body: httpTrace,
      });

      const store = mockStore(initialState);

      return store.dispatch(loadTrace('bb1f0e21882325b8')).then(() => {
        expect(store.getActions().length).toBe(2);
        expect(store.getActions()[0].type).toBe('traces/load/pending');
        expect(store.getActions()[1].type).toBe('traces/load/fulfilled');
        const [, { payload }] = store.getActions();
        expect(payload.rawTrace).toEqual(httpTrace);
        expect(payload.skewCorrectedTrace).toEqual(skewCorrectedTrace);
        expect(payload.adjustedTrace).toEqual(adjustedTrace);
      });
    });
  });
});
