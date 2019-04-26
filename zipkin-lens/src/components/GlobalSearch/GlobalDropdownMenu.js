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
import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';
import Modal from 'react-modal';

import { ensureV2 } from '../../util/trace';

const propTypes = {
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
  loadTrace: PropTypes.func.isRequired,
  loadTraceFailure: PropTypes.func.isRequired,
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
    const { history, loadTrace, loadTraceFailure } = this.props;

    const [file] = event.target.files;
    const fileReader = new FileReader();

    fileReader.onload = () => {
      const { result } = fileReader;

      let rawTrace;
      try {
        rawTrace = JSON.parse(result);
      } catch (error) {
        loadTraceFailure('This file is not JSON');
        history.push({
          pathname: '/zipkin/traceViewer',
        });
        return;
      }
      try {
        ensureV2(rawTrace);
        loadTrace(rawTrace);
      } catch (error) {
        loadTraceFailure('V1 format is not supported');
      }
      history.push({
        pathname: '/zipkin/traceViewer',
      });
    };
    fileReader.onabort = () => {
      loadTraceFailure('Failed to load the file');
      history.push({
        pathname: '/zipkin/traceViewer',
      });
    };
    fileReader.onerror = fileReader.onabort;
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
