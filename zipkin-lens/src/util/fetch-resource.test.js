/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { expect, test } from 'vitest';
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
