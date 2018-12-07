import PropTypes from 'prop-types';
import React from 'react';
import _ from 'lodash';

import VizceralExt from './VizceralExt';

const propTypes = {
  detailedService: PropTypes.string,
  graph: PropTypes.shape({}).isRequired,
  onDetailedServiceChange: PropTypes.func.isRequired,
  searchString: PropTypes.string.isRequired,
};

const defaultProps = {
  detailedService: undefined,
};

const style = {
  colorConnectionLine: 'rgb(160, 150, 160)',
};

class DependenciesGraph extends React.Component {
  constructor(props) {
    super(props);
    this.handleObjectHighlighted = this.handleObjectHighlighted.bind(this);
  }

  handleObjectHighlighted(highlightedObject) {
    const {
      detailedService,
      onDetailedServiceChange,
    } = this.props;

    if (typeof highlightedObject === 'undefined') {
      onDetailedServiceChange(undefined);
      return;
    }

    if (highlightedObject.type === 'node' && highlightedObject.getName() !== detailedService) {
      onDetailedServiceChange(highlightedObject.getName());
    }
  }

  render() {
    const { graph, searchString } = this.props;
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
          match={searchString}
          styles={style}
        />
      </div>
    );
  }
}

DependenciesGraph.propTypes = propTypes;
DependenciesGraph.defaultProps = defaultProps;

export default DependenciesGraph;
