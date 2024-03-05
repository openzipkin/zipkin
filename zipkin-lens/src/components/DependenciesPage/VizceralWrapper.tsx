/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import React from 'react';
import Vizceral from 'vizceral-react';

// Vizceral (vizceral-react) does not release some resources when unmounting the component.
// Therefore, when Vizceral is mounted many times, rendering speed decreases.
// So this class inherits Vizceral and overrides componentWillUnmount for releasing resources.
class VizceralExt extends Vizceral {
  componentWillUnmount() {
    delete this.vizceral;
  }
}

// This component is defined to avoid the strict type checking.
const VizceralWrapper = (props: any) => {
  return <VizceralExt {...props} />;
};

export default VizceralWrapper;
