/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import { ActionTypes } from '../types/action-types';
import * as actions from './dependencies-action';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('dependencies actions', () => {
  it('should create an action to clear dependencies', () => {
    const expectedAction = {
      type: ActionTypes.CLEAR_DEPENDENCIES,
    };
    expect(actions.clearDependencies()).toEqual(expectedAction);
  });
});

describe('dependencies async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create LOAD_DEPENDENCIES_SUCCESS when fetching dependencies has been done', () => {
    fetchMock.getOnce('*', {
      body: [
        {
          parent: 'service1',
          child: 'service2',
          callCount: 100,
          errorCount: 5,
        },
        {
          parent: 'service3',
          child: 'service2',
          callCount: 4,
        },
      ],
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: ActionTypes.LOAD_DEPENDENCIES_REQUEST },
      {
        type: ActionTypes.LOAD_DEPENDENCIES_SUCCESS,
        payload: {
          dependencies: [
            {
              parent: 'service1',
              child: 'service2',
              callCount: 100,
              errorCount: 5,
            },
            {
              parent: 'service3',
              child: 'service2',
              callCount: 4,
            },
          ],
        },
      },
    ];
    const store = mockStore({});

    return store
      .dispatch(actions.loadDependencies({ endTs: 1587132132201 }))
      .then(() => {
        expect(store.getActions()).toEqual(expectedActions);
      });
  });
});
