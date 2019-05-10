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
import Slider from 'rc-slider';

const { Range } = Slider;

const propTypes = {
  duration: PropTypes.number.isRequired,
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  onStartAndEndTsChange: PropTypes.func.isRequired,
};

class MiniTimelineSlider extends React.Component {
  constructor(props) {
    super(props);
    this.state = { isDragging: false };
    this.handleBeforeRangeChange = this.handleBeforeRangeChange.bind(this);
    this.handleAfterRangeChange = this.handleAfterRangeChange.bind(this);
  }

  handleBeforeRangeChange() {
    this.setState({ isDragging: true });
  }

  handleAfterRangeChange(value) {
    const { duration, onStartAndEndTsChange } = this.props;
    onStartAndEndTsChange(
      value[0] * duration / 100,
      value[1] * duration / 100,
    );
    this.setState({ isDragging: false });
  }

  render() {
    const { duration, startTs, endTs } = this.props;
    const { isDragging } = this.state;

    const props = {
      allowCase: false,
      defaultValue: [0, 100],
      step: 0.01,
      onBeforeChange: this.handleBeforeRangeChange,
      onAfterChange: this.handleAfterRangeChange,
    };
    if (!isDragging) {
      props.value = [
        startTs / duration * 100,
        endTs / duration * 100,
      ];
    }
    return (
      <div className="mini-timeline-slider">
        <Range {...props} />
      </div>
    );
  }
}

MiniTimelineSlider.propTypes = propTypes;

export default MiniTimelineSlider;
