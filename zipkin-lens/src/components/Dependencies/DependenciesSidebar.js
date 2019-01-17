import PropTypes from 'prop-types';
import React from 'react';
import { Doughnut } from 'react-chartjs-2';

import ServiceNameBadge from '../Common/ServiceNameBadge';
import { getServiceNameColor } from '../../util/color';

const propTypes = {
  serviceName: PropTypes.string.isRequired,
  targetEdges: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
  sourceEdges: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
};

const renderNoEdges = () => (
  <div className="dependencies-sidebar__no-edges">
    <span className="fas fa-exclamation-circle dependencies-sidebar__no-edges-icon" />
    No services
  </div>
);

const renderEdgeData = (edges, isTarget) => {
  if (edges.length === 0) {
    return renderNoEdges();
  }

  const key = isTarget ? 'target' : 'source';
  return edges.map(edge => (
    <div className="dependencies-sidebar__edge-data">
      <ServiceNameBadge
        serviceName={edge[key]}
      />
      <div className="dependencies-sidebar__count-data-wrapper">
        <div className="dependencies-sidebar__count-data">
          <div className="dependencies-sidebar__count-data-label dependencies-sidebar__count-data-label--normal">
            COUNT
          </div>
          <div className="dependencies-sidebar__count-data-value">
            {edge.metrics.normal}
          </div>
        </div>
        <div className="dependencies-sidebar__count-data">
          <div className="dependencies-sidebar__count-data-label dependencies-sidebar__count-data-label--danger">
            ERROR
          </div>
          <div className="dependencies-sidebar__count-data-value">
            {edge.metrics.danger}
          </div>
        </div>
      </div>
    </div>
  ));
};

const renderDoughnut = (edges, isTarget) => {
  if (edges.length === 0) {
    return null;
  }

  const data = {
    maintainAspectRatio: false,
    responsive: false,
    labels: isTarget ? edges.map(edge => edge.target) : edges.map(edge => edge.source),
    datasets: [{
      data: edges.map(e => e.metrics.normal + e.metrics.danger),
      backgroundColor: isTarget
        ? edges.map(e => getServiceNameColor(e.target))
        : edges.map(e => getServiceNameColor(e.source)),
    }],
  };
  return (
    <div className="dependencies-sidebar__doughnut">
      <Doughnut
        data={data}
        width={420}
        height={540}
      />
    </div>
  );
};

const DependenciesSidebar = ({
  serviceName,
  targetEdges,
  sourceEdges,
}) => (
  <div className="dependencies-sidebar">
    <div className="dependencies-sidebar__service-name-container">
      <div className="dependencies-sidebar__service-name">
        {serviceName}
      </div>
    </div>
    <div className="dependencies-sidebar__subtitle">
      <span className="fas fa-angle-double-down dependencies-sidebar__subtitle-icon" />
      <strong>
        Uses
      </strong>
      (Traced requests)
    </div>
    {renderEdgeData(targetEdges, true)}
    <div className="dependencies-sidebar__doughnut-wrapper">
      {renderDoughnut(targetEdges, true)}
    </div>
    <div className="dependencies-sidebar__separator" />
    <div className="dependencies-sidebar__subtitle">
      <span className="fas fa-angle-double-up dependencies-sidebar__subtitle-icon" />
      <strong>
        Used by
      </strong>
      (Traced requests)
    </div>
    {renderEdgeData(sourceEdges, false)}
    <div className="dependencies-sidebar__doughnut-wrapper">
      {renderDoughnut(sourceEdges, false)}
    </div>
  </div>
);

DependenciesSidebar.propTypes = propTypes;

export default DependenciesSidebar;
