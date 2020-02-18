/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import fetchResource from './fetch-resource';

const newPromise = () => {
  let resolve;
  let reject;
  const promise = new Promise((resolve0, reject0) => {
    resolve = resolve0;
    reject = reject0;
  });
  return { promise, resolve, reject };
};

test('promise thrown when not resolved', async () => {
  const { promise } = newPromise();
  const resource = fetchResource(promise);

  // The shady practice of throwing a promise does not work well with Jest's matchers, so we extract
  // the thrown object ourselves.
  let thrown;
  try {
    resource.read();
  } catch (e) {
    thrown = e;
  }

  expect(thrown).toEqual(promise);
});

test('response returned after resolved', async () => {
  const { promise, resolve } = newPromise();
  const resource = fetchResource(promise);

  resolve('resolved');

  await promise;

  expect(resource.read()).toEqual('resolved');
});

test('error thrown after rejected', async () => {
  const { promise, reject } = newPromise();
  const resource = fetchResource(promise);

  const error = new Error('rejected');
  reject(error);

  try {
    await promise;
  } catch (e) {
    // Ignore
  }

  expect(() => resource.read()).toThrow(error);
});
