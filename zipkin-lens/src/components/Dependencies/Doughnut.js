/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import PropTypes from 'prop-types';
import React from 'react';
import { Doughnut as RCDoughnut } from 'react-chartjs-2';
import { getServiceNameColor } from '../../util/color';

const propTypes = {
  edges: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
  renderTarget: PropTypes.bool.isRequired,
};

const options = {
  legend: {
    labels: {
      fontColor: 'white',
    },
  },
};

const Doughnut = ({ edges, renderTarget }) => {
  const data = {
    maintainAspectRatio: false,
    responsive: false,
    labels: renderTarget ? edges.map(e => e.target) : edges.map(e => e.source),
    datasets: [{
      data: edges.map(e => e.metrics.normal + e.metrics.danger),
      backgroundColor: renderTarget
        ? edges.map(e => getServiceNameColor(e.target))
        : edges.map(e => getServiceNameColor(e.source)),
    }],
  };

  return (
    <div className="dependencies__sidebar-doughnut">
      <RCDoughnut
        data={data}
        width={340}
        height={540}
        options={options}
      />
    </div>
  );
};

Doughnut.propTypes = propTypes;

export default Doughnut;
