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
import { getGraphHeight, getGraphLineHeight } from './util';

describe('getGraphHeight', () => {
  it('should return proper value', () => {
    expect(getGraphHeight(-1)).toEqual(0);
    expect(getGraphHeight(0)).toEqual(0);
    expect(getGraphHeight(1)).toEqual(0);
    expect(getGraphHeight(2)).toEqual(2 * 5);
    expect(getGraphHeight(14)).toEqual(14 * 5);
    expect(getGraphHeight(15)).toEqual(75);
    expect(getGraphHeight(16)).toEqual(75);
    expect(getGraphHeight(100)).toEqual(75);
  });
});

describe('getGraphLineHeight', () => {
  it('should return proper value', () => {
    expect(getGraphLineHeight(-1)).toEqual(0);
    expect(getGraphLineHeight(0)).toEqual(0);
    expect(getGraphLineHeight(1)).toEqual(0);
    expect(getGraphLineHeight(2)).toEqual(5);
    expect(getGraphLineHeight(14)).toEqual(5);
    expect(getGraphLineHeight(15)).toEqual(5);
    expect(getGraphLineHeight(16)).toEqual(75 / 16);
    expect(getGraphLineHeight(20)).toEqual(75 / 20);
    expect(getGraphLineHeight(100)).toEqual(1);
  });
});
