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
import queryString from 'query-string';

import * as types from '../constants/action-types';
import * as api from '../constants/api';
import {
  treeCorrectedForClockSkew,
  traceSummary as buildTraceSummary,
  traceSummaries as buildTraceSummaries,
} from '../zipkin';

export const clearTraces = () => ({
  type: types.CLEAR_TRACES,
});

export const loadTracesRequest = () => ({
  type: types.TRACES_LOAD_REQUEST,
});

export const loadTracesSuccess = (
  traces,
  traceSummaries,
  correctedTraceMap,
  lastQueryParams,
) => ({
  type: types.TRACES_LOAD_SUCCESS,
  traces,
  traceSummaries,
  correctedTraceMap,
  lastQueryParams,
});

export const loadTracesFailure = () => ({
  type: types.TRACES_LOAD_FAILURE,
});

const calculateTraceSummaries = async (traces, serviceName) => {
  const correctedTraces = traces.map(treeCorrectedForClockSkew);

  const correctedTraceMap = {};
  correctedTraces.forEach((trace, index) => {
    const [{ traceId }] = traces[index];
    correctedTraceMap[traceId] = trace;
  });

  const traceSummaries = buildTraceSummaries(
    serviceName,
    correctedTraces.map(buildTraceSummary),
  );

  return {
    traceSummaries,
    correctedTraceMap,
  };
};

export const loadTraces = (params) => async (dispatch) => {
  dispatch(loadTracesRequest());
  try {
    const query = queryString.stringify(params);

    const res = await fetch(`${api.TRACES}?${query}`);

    if (!res.ok) {
      throw Error(res.statusText);
    }
    const traces = await res.json();

    const { traceSummaries, correctedTraceMap } = await calculateTraceSummaries(
      traces,
      query.serviceName,
    );

    dispatch(
      loadTracesSuccess(traces, traceSummaries, correctedTraceMap, params),
    );
  } catch (err) {
    dispatch(loadTracesFailure());
  }
};
