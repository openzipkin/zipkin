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

/* eslint-disable no-param-reassign */

import {
  SerializedError,
  createAsyncThunk,
  createSlice,
} from '@reduxjs/toolkit';

import * as api from '../constants/api';
import AdjustedTrace from '../models/AdjustedTrace';
import Span from '../models/Span';
import TraceSummary from '../models/TraceSummary';
import { ensureV2TraceData } from '../util/trace';

const {
  treeCorrectedForClockSkew,
  traceSummary: buildTraceSummary,
  traceSummaries: buildTraceSummaries,
  detailedTraceSummary: buildDetailedTraceSummary,
} = require('../zipkin');

export const searchTraces = createAsyncThunk(
  'traces/search',
  async (params: { [key: string]: string }) => {
    const ps = new URLSearchParams(params);
    const resp = await fetch(`${api.TRACES}?${ps}`);
    if (!resp.ok) {
      throw Error(resp.statusText);
    }
    const rawTraces = (await resp.json()) as Span[][];
    const traces = rawTraces.reduce(
      (acc, rawTrace) => {
        const [{ traceId }] = rawTrace;
        const skewCorrectedTrace = treeCorrectedForClockSkew(rawTrace);
        acc[traceId] = {
          rawTrace,
          skewCorrectedTrace,
        };
        return acc;
      },
      {} as {
        [key: string]: {
          rawTrace: Span[];
          skewCorrectedTrace: any;
        };
      },
    );

    const traceSummaries = buildTraceSummaries(
      ps.get('serviceName'),
      Object.keys(traces).map((traceId) =>
        buildTraceSummary(traces[traceId].skewCorrectedTrace),
      ),
    );

    return {
      traces,
      traceSummaries,
    };
  },
);

export const loadTrace = createAsyncThunk(
  'traces/load',
  async (traceId: string, thunkApi) => {
    const { traces }: TracesState = (thunkApi.getState() as any).traces;

    if (traces[traceId]) {
      const { rawTrace, skewCorrectedTrace } = traces[traceId];
      let { adjustedTrace } = traces[traceId];
      if (adjustedTrace) {
        return {
          traceId,
          trace: traces[traceId],
        };
      }
      adjustedTrace = buildDetailedTraceSummary(skewCorrectedTrace);
      return {
        traceId,
        trace: {
          rawTrace,
          skewCorrectedTrace,
          adjustedTrace,
        },
      };
    }

    const resp = await fetch(`${api.TRACE}/${traceId}`);
    if (!resp.ok) {
      throw Error(resp.statusText);
    }
    const rawTrace: Span[] = await resp.json();
    const skewCorrectedTrace = treeCorrectedForClockSkew(rawTrace);
    const adjustedTrace: AdjustedTrace = buildDetailedTraceSummary(
      skewCorrectedTrace,
    );
    return {
      traceId,
      trace: {
        rawTrace,
        skewCorrectedTrace,
        adjustedTrace,
      },
    };
  },
);

const readFileAsync = (blob: Blob) => {
  return new Promise<any>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      resolve(reader.result);
    };
    reader.onerror = () => {
      reject(reader.error);
    };
    reader.readAsText(blob);
  });
};

export const loadJsonTrace = createAsyncThunk(
  'traces/loadJson',
  async (blob: Blob) => {
    const rawTraceStr = await readFileAsync(blob);
    const rawTrace: Span[] = JSON.parse(rawTraceStr);
    ensureV2TraceData(rawTrace);
    const [{ traceId }] = rawTrace;
    const skewCorrectedTrace = treeCorrectedForClockSkew(rawTrace);
    const adjustedTrace: AdjustedTrace = buildDetailedTraceSummary(
      skewCorrectedTrace,
    );
    return {
      traceId,
      trace: {
        rawTrace,
        skewCorrectedTrace,
        adjustedTrace,
      },
    };
  },
);

export interface TracesState {
  isLoading: boolean;
  error?: SerializedError;
  traces: {
    [key: string]: { // key is the traceId
      rawTrace: Span[];
      skewCorrectedTrace: any;
      adjustedTrace?: AdjustedTrace;
    };
  };
  traceSummaries: TraceSummary[];
}

const initialState: TracesState = {
  isLoading: false,
  traces: {},
  traceSummaries: [],
  error: undefined,
};

const tracesSlice = createSlice({
  name: 'traces',
  initialState,
  reducers: {
    clearTraceSummaries: (state) => {
      state.traceSummaries = [];
    },
  },
  extraReducers: (builder) => {
    builder.addCase(searchTraces.pending, (state) => {
      state.isLoading = true;
    });
    builder.addCase(searchTraces.fulfilled, (state, action) => {
      const { traces, traceSummaries } = action.payload;
      const newTraces = { ...state.traces };
      Object.keys(traces).forEach((traceId) => {
        newTraces[traceId] = traces[traceId];
      });
      state.isLoading = false;
      state.error = undefined;
      state.traces = newTraces;
      state.traceSummaries = traceSummaries;
    });
    builder.addCase(searchTraces.rejected, (state, action) => {
      state.isLoading = false;
      state.error = action.error;
    });

    builder.addCase(loadTrace.pending, (state) => {
      state.isLoading = true;
    });
    builder.addCase(loadTrace.fulfilled, (state, action) => {
      const { traceId, trace } = action.payload;
      const newTraces = { ...state.traces };
      newTraces[traceId] = trace;
      state.isLoading = false;
      state.traces = newTraces;
    });
    builder.addCase(loadTrace.rejected, (state, action) => {
      state.isLoading = false;
      state.error = action.error;
    });

    builder.addCase(loadJsonTrace.pending, (state) => {
      state.isLoading = true;
    });
    builder.addCase(loadJsonTrace.fulfilled, (state, action) => {
      const { traceId, trace } = action.payload;
      const newTraces = { ...state.traces };
      newTraces[traceId] = trace;
      state.traces = newTraces;
      state.isLoading = false;
    });
    builder.addCase(loadJsonTrace.rejected, (state, action) => {
      state.isLoading = false;
      state.error = action.error;
    });
  },
});

export default tracesSlice;

export const { clearTraceSummaries } = tracesSlice.actions;
