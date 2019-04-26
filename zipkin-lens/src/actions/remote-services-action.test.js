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

import * as actions from './remote-services-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('remoteServices actions', () => {
  it('should create an action to clear remote services', () => {
    const expectedAction = {
      type: types.CLEAR_REMOTE_SERVICES,
    };
    expect(actions.clearRemoteServices()).toEqual(expectedAction);
  });
});

describe('remoteServices async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create FETCH_REMOTE_SERVICES_SUCCESS when fetching remote services has been done', () => {
    fetchMock.getOnce(`${api.REMOTE_SERVICES}?serviceName=serviceA`, {
      body: ['remoteService1', 'remoteService2', 'remoteService3'],
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: types.FETCH_REMOTE_SERVICES_REQUEST },
      {
        type: types.FETCH_REMOTE_SERVICES_SUCCESS,
        remoteServices: ['remoteService1', 'remoteService2', 'remoteService3'],
      },
    ];
    const store = mockStore({});

    return store.dispatch(actions.fetchRemoteServices('serviceA')).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
