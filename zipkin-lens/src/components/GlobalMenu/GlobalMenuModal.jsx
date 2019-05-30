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
import ReactModal from 'react-modal';

import GlobalMenuTraceId from './GlobalMenuTraceId';
import GlobalMenuJsonSelector from './GlobalMenuJsonSelector';

const propTypes = {
  isOpened: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
};

const GlobalMenuModal = ({ isOpened, onClose }) => (
  <div className="global-menu-modal">
    <ReactModal
      className="global-menu-modal__modal"
      isOpen={isOpened}
      onRequestClose={onClose}
    >
      <div className="global-menu-modal__trace-id-wrapper">
        <GlobalMenuTraceId />
      </div>
      <div className="global-menu-modal__json-selector-wrapper">
        <GlobalMenuJsonSelector />
      </div>
    </ReactModal>
  </div>
);

GlobalMenuModal.propTypes = propTypes;

export default GlobalMenuModal;
