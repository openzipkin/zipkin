import queryString from 'query-string';

import * as types from '../constants/action-types';
import * as api from '../constants/api';

export const fetchDependenciesRequest = () => ({
  type: types.FETCH_DEPENDENCIES_REQUEST,
});

export const fetchDependenciesSuccess = dependencies => ({
  type: types.FETCH_DEPENDENCIES_SUCCESS,
  dependencies,
});

export const fetchDependenciesFailure = () => ({
  type: types.FETCH_DEPENDENCIES_FAILURE,
});

const fetchDependenciesTimeout = 500;

export const fetchDependencies = params => async (dispatch) => {
  dispatch(fetchDependenciesRequest());
  try {
    const query = queryString.stringify(params);

    const res = await Promise.all([
      fetch(`${api.DEPENDENCIES}?${query}`),
      new Promise(resolve => setTimeout(resolve, fetchDependenciesTimeout)),
    ]);
    if (!res[0].ok) {
      throw Error(res[0].statusText);
    }
    const dependencies = await res[0].json();
    dispatch(fetchDependenciesSuccess(dependencies));
  } catch (err) {
    dispatch(fetchDependenciesFailure());
  }
};

export const clearDependencies = () => ({
  type: types.CLEAR_DEPENDENCIES,
});
