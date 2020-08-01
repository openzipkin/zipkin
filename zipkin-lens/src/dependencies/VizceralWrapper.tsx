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

import React from 'react';
import Vizceral from 'vizceral-react';

// Vizceral (vizceral-react) does not release some resources when unmounting the component.
// Therefore, when Vizceral is mounted many times, rendering speed decreases.
// So this class inherits Vizceral and overrides componentWillUnmount for releasing resources.
class VizceralExt extends Vizceral {
  componentWillUnmount() {
    this.vizceral.animate = () => {};
    delete this.vizceral;
  }
}

// This component is defined to avoid the strict type checking.
const VizceralWrapper = (props: any) => {
  return <VizceralExt {...props} />;
};

export default VizceralWrapper;
