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
import _ from 'lodash';

import VizceralExt from './VizceralExt';

const propTypes = {
  selectedServiceName: PropTypes.string,
  graph: PropTypes.shape({}).isRequired,
  onServiceSelect: PropTypes.func.isRequired,
  filter: PropTypes.string.isRequired,
};

const defaultProps = {
  selectedServiceName: undefined,
};

const style = {
  colorText: 'rgb(50, 50, 50)',
  colorTextDisabled: 'rgb(50, 50, 50)',
  colorConnectionLine: 'rgb(50, 50, 50)',
  colorTraffic: {
    normal: 'rgb(145, 200, 220)',
    warning: 'rgb(255, 75, 75)',
    danger: 'rgb(255, 75, 75)',
  },
  colorDonutInternalColor: 'rgb(245, 245, 245)',
  colorDonutInternalColorHighlighted: 'rgb(145, 200, 220)',
  colorLabelBorder: 'rgb(85, 140, 160)',
  colorLabelText: 'rgb(50, 50, 50)',
  colorTrafficHighlighted: {
    normal: 'rgb(155, 210, 230)',
  },
};

class DependenciesGraph extends React.Component {
  constructor(props) {
    super(props);
    this.handleObjectHighlighted = this.handleObjectHighlighted.bind(this);
  }

  handleObjectHighlighted(highlightedObject) {
    const { selectedServiceName, onServiceSelect } = this.props;
    if (typeof highlightedObject === 'undefined') {
      onServiceSelect(undefined);
      return;
    }
    if (highlightedObject.type === 'node' && highlightedObject.getName() !== selectedServiceName) {
      onServiceSelect(highlightedObject.getName());
    }
  }

  render() {
    const { graph, filter } = this.props;
    let maxVolume = 0;
    if (graph.allEdges().length > 0) {
      const maxVolumeEdge = _.maxBy(
        graph.allEdges(), edge => edge.metrics.normal + edge.metrics.danger,
      );
      maxVolume = maxVolumeEdge.metrics.normal + maxVolumeEdge.metrics.danger;
    }

    return (
      <div className="dependencies__graph">
        <VizceralExt
          allowDraggingOfNodes
          targetFramerate={15}
          traffic={{
            renderer: 'region',
            layout: 'ltrTree',
            name: 'dependencies-graph',
            updated: new Date().getTime(),
            maxVolume: maxVolume * 2000,
            nodes: graph.allNodes(),
            connections: graph.allEdges(),
          }}
          objectHighlighted={this.handleObjectHighlighted}
          match={filter}
          styles={style}
        />
      </div>
    );
  }
}

DependenciesGraph.propTypes = propTypes;
DependenciesGraph.defaultProps = defaultProps;

export default DependenciesGraph;
