import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';
import Modal from 'react-modal';

const propTypes = {
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
  setTrace: PropTypes.func.isRequired,
};

// This selector (class name) is used to specify a modal parent component.
const modalWrapperClass = 'global-dropdown-menu__modal-wrapper';

class GlobalDropdownMenu extends React.Component {
  constructor(props) {
    super(props);
    this.fileInpueElement = undefined;
    this.state = {
      isModalOpened: false,
      traceId: '',
    };
    this.handleOpenModalToggle = this.handleOpenModalToggle.bind(this);
    this.handleTraceIdButtonClick = this.handleTraceIdButtonClick.bind(this);
    this.handleTraceIdChange = this.handleTraceIdChange.bind(this);
    this.handleTraceJsonChange = this.handleTraceJsonChange.bind(this);
  }

  handleOpenModalToggle() {
    const { isModalOpened } = this.state;
    this.setState({ isModalOpened: !isModalOpened });
  }

  handleTraceIdButtonClick(event) {
    const { history } = this.props;
    const { traceId } = this.state;
    history.push({
      pathname: `/zipkin/traces/${traceId}`,
    });
    this.setState({ isModalOpened: false });
    event.stopPropagation();
  }

  handleTraceIdChange(event) {
    this.setState({
      traceId: event.target.value,
    });
  }

  handleTraceJsonChange(event) {
    const { history, setTrace } = this.props;

    const [file] = event.target.files;
    const fileReader = new FileReader();

    fileReader.onload = () => {
      const { result } = fileReader;
      let rawTrace;
      try {
        rawTrace = JSON.parse(result);
        setTrace(rawTrace);
        history.push({
          pathname: '/zipkin/traceViewer',
        });
      } catch (error) {
        // Do nothing
      }
    };
    fileReader.readAsText(file);
    this.setState({ isModalOpened: false });
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
          <div className="global-dropdown-menu__trace-id-search">
            <input
              className="global-dropdown-menu__trace-id-input"
              type="text"
              value={traceId}
              onChange={this.handleTraceIdChange}
            />
            <span
              className="global-dropdown-menu__trace-id-button"
              role="presentation"
              onClick={this.handleTraceIdButtonClick}
            >
              <i className="fas fa-search" />
            </span>
          </div>
        </div>
        <div className="global-dropdown-menu__trace-json">
          <input
            type="file"
            style={{ display: 'none' }}
            ref={(element) => { this.fileInpueElement = element; }}
            onChange={this.handleTraceJsonChange}
          />
          <div
            role="presentation"
            onClick={() => { this.fileInpueElement.click(); }}
          >
            Choose JSON file...
          </div>
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
