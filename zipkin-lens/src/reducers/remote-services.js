import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  remoteServices: [],
};

const remoteServices = (state = initialState, action) => {
  switch (action.type) {
    case types.FETCH_REMOTE_SERVICES_REQUEST:
      return {
        ...state,
        isLoading: true,
        remoteServices: [], /* Initialize remote services */
      };
    case types.FETCH_REMOTE_SERVICES_SUCCESS:
      return {
        ...state,
        isLoading: false,
        remoteServices: action.remoteServices,
      };
    case types.FETCH_REMOTE_SERVICES_FAILURE:
      return {
        ...state,
        isLoading: false,
        remoteServices: [],
      };
    case types.CLEAR_REMOTE_SERVICES:
      return {
        ...state,
        remoteServices: [],
      };
    default:
      return state;
  }
};

export default remoteServices;
