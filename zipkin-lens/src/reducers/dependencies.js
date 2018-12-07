import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  dependencies: [],
};

const dependencies = (state = initialState, action) => {
  switch (action.type) {
    case types.FETCH_DEPENDENCIES_REQUEST:
      return {
        ...state,
        isLoading: true,
      };
    case types.FETCH_DEPENDENCIES_SUCCESS:
      return {
        ...state,
        isLoading: false,
        dependencies: action.dependencies,
      };
    case types.FETCH_DEPENDENCIES_FAILURE:
      return {
        ...state,
        isLoading: false,
        dependencies: [],
      };
    case types.CLEAR_DEPENDENCIES:
      return {
        ...state,
        dependencies: [],
      };
    default:
      return state;
  }
};

export default dependencies;
