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
