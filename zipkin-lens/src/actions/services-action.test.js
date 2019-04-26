/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import * as actions from './services-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('services async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create FETCH_SERVICES_SUCCESS when fetching services has been done', () => {
    fetchMock.getOnce(api.SERVICES, {
      body: ['serviceA', 'serviceB', 'serviceC'],
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: types.FETCH_SERVICES_REQUEST },
      {
        type: types.FETCH_SERVICES_SUCCESS,
        services: ['serviceA', 'serviceB', 'serviceC'],
      },
    ];
    const store = mockStore({});

    return store.dispatch(actions.fetchServices()).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
