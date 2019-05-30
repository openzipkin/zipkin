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
import { connect } from 'react-redux';

import { ensureV2TraceData } from '../../util/trace';
import { loadTrace as loadTraceActionCreator, loadTraceFailure as loadTraceFailureActionCreator } from '../../actions/trace-viewer-action';

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  loadTrace: PropTypes.func.isRequired,
  loadTraceFailure: PropTypes.func.isRequired,
};

class GlobalMenuJsonSelector extends React.Component {
  constructor(props) {
    super(props);
    this.inputElement = undefined;
    this.handleFileChange = this.handleFileChange.bind(this);
    this.handleClick = this.handleClick.bind(this);
  }

  goToTraceViewerPage() {
    const { history } = this.props;
    history.push({ pathname: '/zipkin/traceViewer' });
  }

  handleClick(event) {
    if (this.inputElement) {
      this.inputElement.click();
    }
    event.stopPropagation();
  }

  handleFileChange(event) {
    const { loadTrace, loadTraceFailure } = this.props;

    const fileReader = new FileReader();

    fileReader.onload = () => {
      const { result } = fileReader;

      let rawTraceData;
      try {
        rawTraceData = JSON.parse(result);
      } catch (error) {
        loadTraceFailure('This file does not contain JSON');
        this.goToTraceViewerPage();
        return;
      }

      try {
        ensureV2TraceData(rawTraceData);
        loadTrace(rawTraceData);
      } catch (error) {
        loadTraceFailure('Only V2 format is supported');
      }
      this.goToTraceViewerPage();
    };

    fileReader.onabort = () => {
      loadTraceFailure('Failed to load this file');
      this.goToTraceViewerPage();
    };

    FileReader.onerror = fileReader.onabort;

    const [file] = event.target.files;
    fileReader.readAsText(file);
  }

  render() {
    return (
      <div className="global-menu-json-selector">
        <input
          type="file"
          className="global-menu-json-selector__input"
          ref={(elem) => { this.inputElement = elem; }}
          onChange={this.handleFileChange}
        />
        <button
          type="button"
          className="global-menu-json-selector__button"
          onClick={this.handleClick}
        />
      </div>
    );
  }
}

GlobalMenuJsonSelector.propTypes = propTypes;

export default connect(
  null,
  dispatch => ({
    loadTrace: trace => dispatch(loadTraceActionCreator(trace)),
    loadTraceFailure: errorMessage => dispatch(loadTraceFailureActionCreator(errorMessage)),
  }),
)(withRouter(GlobalMenuJsonSelector));
