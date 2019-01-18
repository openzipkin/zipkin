import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  autocompleteValues: [],
};

const autocompleteValues = (state = initialState, action) => {
  switch (action.type) {
    case types.FETCH_AUTOCOMPLETE_VALUES_REQUEST:
      return {
        ...state,
        isLoading: true,
      };
    case types.FETCH_AUTOCOMPLETE_VALUES_SUCCESS:
      return {
        ...state,
        isLoading: false,
        autocompleteValues: action.autocompleteValues,
      };
    case types.FETCH_AUTOCOMPLETE_VALUES_FAILURE:
      return {
        ...state,
        isLoading: false,
        autocompleteValues: [],
      };
    default:
      return state;
  }
};

export default autocompleteValues;
