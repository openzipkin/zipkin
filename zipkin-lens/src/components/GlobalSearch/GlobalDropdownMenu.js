import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';
import Modal from 'react-modal';

const propTypes = {
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
};

// This selector (class name) is used to specify a modal parent component.
const modalWrapperClass = 'global-dropdown-menu__modal-wrapper';

class GlobalDropdownMenu extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      isModalOpened: false,
      traceId: '',
    };
    this.handleOpenModalToggle = this.handleOpenModalToggle.bind(this);
    this.handleTraceIdKeyDown = this.handleTraceIdKeyDown.bind(this);
    this.handleTraceIdChange = this.handleTraceIdChange.bind(this);
  }

  handleOpenModalToggle() {
    const { isModalOpened } = this.state;
    this.setState({ isModalOpened: !isModalOpened });
  }

  handleTraceIdKeyDown(event) {
    const { history } = this.props;
    const { traceId } = this.state;
    if (event.key === 'Enter') {
      history.push({
        pathname: `/zipkin/traces/${traceId}`,
      });
    }
    event.stopPropagation();
  }

  handleTraceIdChange(event) {
    this.setState({
      traceId: event.target.value,
    });
  }

  renderModal() {
    const { isModalOpened, traceId } = this.state;
    return (
      <Modal
        className="global-dropdown-menu__modal"
        overlayClassName="global-dropdown-menu__overlay"
        isOpen={isModalOpened}
        parentSelector={() => document.querySelector(`.${modalWrapperClass}`)}
      >
        <div className="global-dropdown-menu__trace-id">
          <div className="global-dropdown-menu__trace-id-label">Trace ID</div>
          <input
            className="global-dropdown-menu__trace-id-input"
            type="text"
            value={traceId}
            onChange={this.handleTraceIdChange}
            onKeyDown={this.handleTraceIdKeyDown}
          />
        </div>
        <div className="global-dropdown-menu__menu">
          Open JSON
        </div>
      </Modal>
    );
  }

  render() {
    return (
      <div className="global-dropdown-menu">
        <div
          className="global-dropdown-menu__button"
          onClick={this.handleOpenModalToggle}
          role="presentation"
        >
          <i className="fas fa-bars" />
        </div>
        <div className={modalWrapperClass}>
          {this.renderModal()}
        </div>
      </div>
    );
  }
}

GlobalDropdownMenu.propTypes = propTypes;

export default withRouter(GlobalDropdownMenu);
