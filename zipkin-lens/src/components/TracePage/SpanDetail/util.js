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
// Annotation's value may not be unique, so timestamp is also used in annotation key.
export const generateAnnotationKey = (annotation) =>
  `${annotation.value}-${annotation.timestamp}`;

// Tag's key may not be unique, so value is also used.
export const generateTagKey = (tag) => `${tag.key}-${tag.value}`;
