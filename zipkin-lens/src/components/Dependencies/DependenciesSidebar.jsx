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
    labels: isTarget ? edges.map(edge => edge.target) : edges.map(edge => edge.source),
    datasets: [{
      data: edges.map(e => e.metrics.normal + e.metrics.danger),
      backgroundColor: isTarget
        ? edges.map(e => getServiceNameColor(e.target))
        : edges.map(e => getServiceNameColor(e.source)),
    }],
  };
  const options = {
    maintainAspectRatio: false,
    legend: {
      display: false,
    },
  };
  return (
    <div className="dependencies-sidebar__doughnut">
      <Doughnut
        width={320}
        height={320}
        data={data}
        options={options}
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
