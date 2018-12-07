import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';

import Input from '../../Common/Input';

const propTypes = {
  history: PropTypes.shape({}).isRequired,
};

class TraceId extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      traceId: '',
    };
    this.handleTextChange = this.handleTextChange.bind(this);
    this.handleKeyPress = this.handleKeyPress.bind(this);
  }

  handleTextChange(traceId) {
    this.setState({
      traceId,
    });
  }

  handleKeyPress(e) {
    const { history } = this.props;
    const { traceId } = this.state;
    if (e.key === 'Enter') {
      history.push(`/zipkin/trace/${traceId}`);
    }
  }

  render() {
    const { traceId } = this.state;
    return (
      <Input
        className="header__trace-id"
        placeholder="Go to trace"
        value={traceId}
        onChange={this.handleTextChange}
        onKeyPress={this.handleKeyPress}
      />
    );
  }
}

TraceId.propTypes = propTypes;

export default withRouter(TraceId);
