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
import loadConfig from '../js/config';

const sinon = require('sinon');

describe('Config Data', () => {
  let server;

  before(() => {
    server = sinon.fakeServer.create();
    server.respondImmediately = true;
  });
  after(() => { server.restore(); });

  it('searchEnabled defaults to true', (done) => {
    server.respondWith('config.json', [
      200, {'Content-Type': 'application/json'}, JSON.stringify({})
    ]);

    loadConfig().then(config => {
      config('searchEnabled').should.equal(true);
      done();
    });
  });

  // This tests false can override true!
  it('should parse searchEnabled false value', (done) => {
    server.respondWith('config.json', [
      200, {'Content-Type': 'application/json'}, JSON.stringify(
        {searchEnabled: false}
      )
    ]);

    loadConfig().then(config => {
      config('searchEnabled').should.equal(false);
      done();
    });
  });
});
