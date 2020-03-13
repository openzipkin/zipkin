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
import * as types from '../constants/action-types';
import * as api from '../constants/api';
import {
  treeCorrectedForClockSkew,
  detailedTraceSummary as buildDetailedTraceSummary,
} from '../zipkin';

export const loadTraceRequest = () => ({
  type: types.TRACE_LOAD_REQUEST,
});

export const loadTraceSuccess = (traceSummary) => ({
  type: types.TRACE_LOAD_SUCCESS,
  traceSummary,
});

export const loadTraceFailure = () => ({
  type: types.TRACE_LOAD_FAILURE,
});

const calculateCorrectedTrace = async (trace) =>
  treeCorrectedForClockSkew(trace);

const calculateDetailedTraceSummary = async (correctedTrace) =>
  buildDetailedTraceSummary(correctedTrace);

export const loadTrace = (traceId, correctedTraceMap) => async (dispatch) => {
  dispatch(loadTraceRequest());

  if (correctedTraceMap[traceId]) {
    const detailedTraceSummary = await calculateDetailedTraceSummary(
      correctedTraceMap[traceId],
    );
    dispatch(loadTraceSuccess(detailedTraceSummary));
  } else {
    try {
      const res = await fetch(`${api.TRACE}/${traceId}`);

      if (!res.ok) {
        throw Error(res.statusText);
      }
      const trace = await res.json();
      const correctedTrace = await calculateCorrectedTrace(trace);
      const detailedTraceSummary = await calculateDetailedTraceSummary(
        correctedTrace,
      );

      dispatch(loadTraceSuccess(detailedTraceSummary));
    } catch (err) {
      dispatch(loadTraceFailure());
    }
  }
};
