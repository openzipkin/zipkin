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
