import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  autocompleteKeys: [],
};

const autocompleteKeys = (state = initialState, action) => {
  switch (action.type) {
    case types.FETCH_AUTOCOMPLETE_KEYS_REQUEST:
      return {
        ...state,
        isLoading: true,
      };
    case types.FETCH_AUTOCOMPLETE_KEYS_SUCCESS:
      return {
        ...state,
        isLoading: false,
        autocompleteKeys: action.autocompleteKeys,
      };
    case types.FETCH_AUTOCOMPLETE_KEYS_FAILURE:
      return {
        ...state,
        isLoading: false,
        autocompleteKeys: [],
      };
    default:
      return state;
  }
};

export default autocompleteKeys;
