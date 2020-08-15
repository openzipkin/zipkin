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

export default 1;

// /* eslint-disable no-param-reassign */

// import {
//   SerializedError,
//   createAsyncThunk,
//   createSlice,
// } from '@reduxjs/toolkit';

// import * as api from '../constants/api';
// import Span from '../models/Span';
// import TraceSummary from '../models/TraceSummary';
// import TraceData from '../models/TraceData';
// import { treeCorrectedForClockSkew, detailedTraceSummary, traceSummary } from '../zipkin';

// export const loadTraces = createAsyncThunk(
//   'traces/fetch',
//   async (params: { [key: string]: string }) => {
//     const queryParams = new URLSearchParams(params);
//     const resp = await fetch(`${api.TRACES}?${queryParams.toString()}`);
//     if (!resp.ok) {
//       throw Error(resp.statusText);
//     }
//     // const traces = (await resp.json()) as Span[][];

//     /*
//     traces.reduce(
//       (acc, cur) => {
//         const [{ traceId }] = cur;
//         const skewCorrectedData = treeCorrectedForClockSkew(cur);
//         const data = traceSummary(skewCorrectedData);
//         return acc;
//       },
//       {} as {
//         [key: string]: {
//           isLoading: boolean;
//           error?: SerializedError;
//           data: {}; // Used in trace page.
//           summaryData?: TraceSummary; // Used in search page.
//           rawData: Span[];
//           skewCorrectedData: {};
//         };
//       },
//     );
//     */
//     return null;
//   },
// );

// export const loadTrace = createAsyncThunk<
//   TraceData,
//   string,
//   {
//     state: TracesState;
//   }
// >('trace/fetch', async (traceId, thunkApi) => {
//   const { traces } = thunkApi.getState();

//   // Fetch if trace has not yet been fetched.
//   if (
//     !traces[traceId] ||
//     !traces[traceId].rawData ||
//     !traces[traceId].skewCorrectedData
//   ) {
//     const resp = await fetch(`${api.TRACE}/${traceId}`);
//     if (!resp.ok) {
//       throw Error(resp.statusText);
//     }
//     const rawData = await resp.json();
//     const skewCorrectedData = treeCorrectedForClockSkew(rawData);
//     const data = detailedTraceSummary(skewCorrectedData);
//     return {
//       data,
//       rawData,
//       skewCorrectedData,
//     };
//   }

//   // If the calculated data already exists, they are returned as is.
//   if (traces[traceId].data) {
//     return {
//       data: traces[traceId].data,
//       rawData: traces[traceId].rawData,
//       skewCorrectedData: traces[traceId].skewCorrectedData,
//     };
//   }

//   // If the only skew is corrected, calculate data.
//   const data = detailedTraceSummary(traces[traceId].skewCorrectedData);
//   return {
//     data,
//     rawData: traces[traceId].rawData,
//     skewCorrectedData: traces[traceId].skewCorrectedData,
//   };
// });

// export interface TracesState {
//   isLoading: boolean;
//   error?: SerializedError;
//   traces: {
//     [key: string]: {
//       isLoading: boolean;
//       error?: SerializedError;
//       data: {}; // Used in trace page.
//       summaryData?: TraceSummary; // Used in search page.
//       rawData: Span[];
//       skewCorrectedData: {};
//     };
//   };
// }

// const initialState: TracesState = {
//   isLoading: false,
//   traces: {},
//   error: undefined,
// };

// const tracesSlice = createSlice({
//   name: 'traces',
//   initialState,
//   reducers: {},
//   extraReducers: (builder) => {
//     builder.addCase(loadTraces.pending, (state) => {
//       state.isLoading = true;
//       state.traces = {};
//       state.error = undefined;
//     });
//     builder.addCase(loadTraces.fulfilled, (state, action) => {
//       state.isLoading = false;
//       state.error = undefined;
//     });

//   },
// });

// export default tracesSlice;
