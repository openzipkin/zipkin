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
