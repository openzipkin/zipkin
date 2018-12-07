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

export default VizceralExt;
