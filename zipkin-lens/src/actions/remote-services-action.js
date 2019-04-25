import queryString from 'query-string';

import * as types from '../constants/action-types';
import * as api from '../constants/api';

export const fetchRemoteServicesRequest = () => ({
  type: types.FETCH_REMOTE_SERVICES_REQUEST,
});

export const fetchRemoteServicesSuccess = remoteServices => ({
  type: types.FETCH_REMOTE_SERVICES_SUCCESS,
  remoteServices,
});

export const fetchRemoteServicesFailure = () => ({
  type: types.FETCH_REMOTE_SERVICES_FAILURE,
});

export const fetchRemoteServices = serviceName => async (dispatch) => {
  dispatch(fetchRemoteServicesRequest());
  try {
    const query = queryString.stringify({ serviceName });
    const res = await fetch(`${api.REMOTE_SERVICES}?${query}`);
    if (!res.ok) {
      throw Error(res.statusText);
    }
    const remoteServices = await res.json();
    dispatch(fetchRemoteServicesSuccess(remoteServices));
  } catch (err) {
    dispatch(fetchRemoteServicesFailure());
  }
};

export const clearRemoteServices = () => ({
  type: types.CLEAR_REMOTE_SERVICES,
});
