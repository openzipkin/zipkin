import * as types from '../constants/action-types';
import * as api from '../constants/api';

export const fetchServicesRequest = () => ({
  type: types.FETCH_SERVICES_REQUEST,
});

export const fetchServicesSuccess = services => ({
  type: types.FETCH_SERVICES_SUCCESS,
  services,
});

export const fetchServicesFailure = () => ({
  type: types.FETCH_SERVICES_FAILURE,
});

export const fetchServices = () => async (dispatch) => {
  dispatch(fetchServicesRequest());
  try {
    const res = await fetch(api.SERVICES);
    if (!res.ok) {
      throw Error(res.statusText);
    }
    const services = await res.json();
    // alphabetically sort service names since the api might
    // return them out of order
    dispatch(fetchServicesSuccess(services.sort()));
  } catch (err) {
    dispatch(fetchServicesFailure());
  }
};
