/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * Converts a promise to a resource as used in React Suspense nomenclature. A resource provides a
 * read() method which will either suspend rendering until the promise is resolved or return / throw
 * the result of the promise if it is already resolved. This allows React Suspense to continue
 * rendering work while the promise is being resolved.
 */
export default (promise) => {
  let response;
  let error;

  // In Javascript, there is no way to synchronously know whether a promise is resolved. Even if
  // it's already resolved, we are guaranteed to suspend once. Since it's unlikely the promise has
  // resolved at this point anyways, it's not a huge deal though.
  promise.then(
    (resp) => {
      response = resp;
    },
    (err) => {
      error = err;
    },
  );

  return {
    read() {
      if (error) {
        throw error;
      } else if (response) {
        return response;
      } else {
        // Throwing a promise is how to tell React to suspend rendering until it is resolved.
        throw promise;
      }
    },
  };
};
