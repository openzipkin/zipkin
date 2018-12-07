import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  services: [],
};

const services = (state = initialState, action) => {
  switch (action.type) {
    case types.FETCH_SERVICES_REQUEST:
      return {
        ...state,
        isLoading: true,
        services: [], /* Initialize services */
      };
    case types.FETCH_SERVICES_SUCCESS:
      return {
        ...state,
        isLoading: false,
        services: action.services,
      };
    case types.FETCH_SERVICES_FAILURE:
      return {
        ...state,
        isLoading: false,
        services: [],
      };
    default:
      return state;
  }
};

export default services;
