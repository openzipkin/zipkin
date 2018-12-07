/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

export const getErrorTypeColor = (errorType) => {
  switch (errorType) {
    case 'transient':
      return '#F4E689';
    case 'critical':
      return '#FF0000';
    default:
      return '#5C904B';
  }
};

export const getServiceNameColor = (serviceName) => {
  switch (serviceName.length % 10) {
    case 0:
      return '#00ACED';
    case 1:
      return '#5AE628';
    case 2:
      return '#CD201F';
    case 3:
      return '#D1AD59';
    case 4:
      return '#FF5A60';
    case 5:
      return '#563D7C';
    case 6:
      return '#00B489';
    case 7:
      return '#F8630E';
    case 8:
      return '#FFFFFF';
    case 9:
      return '#FF5700';
    default:
      return '#FFFFFF';
  }
};
