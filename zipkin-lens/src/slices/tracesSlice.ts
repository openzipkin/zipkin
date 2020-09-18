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
  async (params: { [key: string]: string }, thunkApi) => {
    const ps = new URLSearchParams(params);

    // We need to import RootState in order to give the type to getState.
    // Importing RootState will result in a cyclic import.
    // So use any type to avoid this.
    const { search, traces }: TracesState = (thunkApi.getState() as any).traces;
    // If the query is the same as the previous query, it will not fetch again.
    if (search.prevQuery === ps.toString()) {
      return {
        traces,
        traceSummaries: search.traceSummaries,
        query: ps.toString(),
      };
    }

    const resp = await fetch(`${api.TRACES}?${ps.toString()}`);
    if (!resp.ok) {
      throw Error(resp.statusText);
    }
    const rawTraces: Span[][] = await resp.json();

    const newTraces = rawTraces.reduce(
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

    const traceSummaries: TraceSummary[] = buildTraceSummaries(
      ps.get('serviceName'),
      Object.keys(newTraces).map((traceId) =>
        buildTraceSummary(newTraces[traceId].skewCorrectedTrace),
      ),
    );

    return {
      traces: newTraces,
      traceSummaries,
      query: ps.toString(),
    };
  },
);

export const loadTrace = createAsyncThunk(
  'traces/load',
  async (traceId: string, thunkApi) => {
    // We need to import RootState in order to give the type to getState.
    // Importing RootState will result in a cyclic import.
    // So use any type to avoid this.
    const { traces }: TracesState = (thunkApi.getState() as any).traces;

    if (traces[traceId]) {
      const { rawTrace, skewCorrectedTrace } = traces[traceId];
      let { adjustedTrace } = traces[traceId];
      if (adjustedTrace) {
        // this trace has already been calculated by buildDetailedTraceSummary
        return traces[traceId];
      }
      if (skewCorrectedTrace) {
        adjustedTrace = buildDetailedTraceSummary(skewCorrectedTrace);
        return {
          rawTrace,
          skewCorrectedTrace,
          adjustedTrace,
        };
      }
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
      rawTrace,
      skewCorrectedTrace,
      adjustedTrace,
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
  traces: {
    [traceId: string]: {
      // When fetching a specific trace, these isLoading and error states are used.
      // They are not used in the search.
      isLoading?: boolean;
      error?: SerializedError;

      rawTrace?: Span[];
      adjustedTrace?: AdjustedTrace;
      // This is a trace data with only the clock-skew modified.
      // It is the intermediate data used to optimize the conversion process of the trace.
      skewCorrectedTrace?: any;
    };
  };
  search: {
    // When searching, isLoading and error states are used.
    // They are not used when fetching a specific trace.
    isLoading: boolean;
    error?: SerializedError;
    // Save the previous query to avoid doing the same query unnecessarily.
    prevQuery?: string;
    traceSummaries: TraceSummary[];
  };
}

const initialState: TracesState = {
  traces: {},
  search: {
    isLoading: false,
    error: undefined,
    prevQuery: undefined,
    traceSummaries: [],
  },
};

const tracesSlice = createSlice({
  name: 'traces',
  initialState,
  reducers: {
    clearSearch: (state) => {
      state.search.isLoading = false;
      state.search.error = undefined;
      state.search.prevQuery = undefined;
      state.search.traceSummaries = [];
    },
  },
  extraReducers: (builder) => {
    builder.addCase(searchTraces.pending, (state) => {
      const newSearchState = {
        ...state.search,
        isLoading: true,
        error: undefined,
      };
      state.search = newSearchState;
    });

    builder.addCase(searchTraces.fulfilled, (state, action) => {
      const { traces } = action.payload;
      const newTraces = { ...state.traces };
      Object.keys(traces).forEach((traceId) => {
        newTraces[traceId] = traces[traceId];
      });
      const newSearchState = {
        isLoading: false,
        error: undefined,
        prevQuery: action.payload.query,
        traceSummaries: action.payload.traceSummaries,
      };
      state.search = newSearchState;
      state.traces = newTraces;
    });

    builder.addCase(searchTraces.rejected, (state, action) => {
      const newSearchState = {
        ...state.search,
        isLoading: false,
        error: action.error,
      };
      state.search = newSearchState;
    });

    builder.addCase(loadTrace.pending, (state, action) => {
      const traceId = action.meta.arg;
      const newTraces = { ...state.traces };
      if (!newTraces[traceId]) {
        newTraces[traceId] = {};
      }
      newTraces[traceId].isLoading = true;
      newTraces[traceId].error = undefined;
      state.traces = newTraces;
    });

    builder.addCase(loadTrace.fulfilled, (state, action) => {
      const traceId = action.meta.arg;
      const newTraces = { ...state.traces };
      newTraces[traceId] = { ...action.payload };
      newTraces[traceId].isLoading = false;
      newTraces[traceId].error = undefined;
      state.traces = newTraces;
    });

    builder.addCase(loadTrace.rejected, (state, action) => {
      const traceId = action.meta.arg;
      const newTraces = { ...state.traces };
      newTraces[traceId].isLoading = false;
      newTraces[traceId].error = action.error;
      state.traces = newTraces;
    });

    // It's easier to handle isLoading and error statuses on the component side,
    // so don't change them here.
    builder.addCase(loadJsonTrace.fulfilled, (state, action) => {
      const { traceId, trace } = action.payload;
      const newTraces = { ...state.traces };
      newTraces[traceId] = trace;
      state.traces = newTraces;
    });
  },
});

export default tracesSlice;

export const { clearSearch } = tracesSlice.actions;
