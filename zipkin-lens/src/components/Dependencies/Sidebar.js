import PropTypes from 'prop-types';
import React from 'react';
import { CSSTransition } from 'react-transition-group';

import Doughnut from './Doughnut';
import Badge from '../Common/Badge';
import { getServiceNameColor } from '../../util/color';

const propTypes = {
  isActive: PropTypes.bool.isRequired,
  detailedService: PropTypes.string.isRequired,
  graph: PropTypes.shape({}).isRequired,
  onSearchStringChange: PropTypes.func.isRequired,
};

const Sidebar = ({
  detailedService, graph, onSearchStringChange, isActive,
}) => {
  const targetEdges = graph.getTargetEdges(detailedService);
  const sourceEdges = graph.getSourceEdges(detailedService);

  return (
    <CSSTransition
      in={isActive}
      classNames="dependencies__sidebar-animation"
      timeout={150}
    >
      <nav className="dependencies__sidebar">
        <div className="dependencies__sidebar-content-wrapper">
          <div
            className="dependencies__sidebar-service-name"
            style={{
              borderColor: getServiceNameColor(detailedService),
            }}
          >
            { detailedService }
          </div>
          <div className="dependencies__sidebar-category-label">
            <i className="fas fa-angle-double-down" />
            &nbsp;
            Uses (Traced Requests)
          </div>
          {
            targetEdges.length === 0
              ? (
                <div className="dependencies__sidebar-category-wrapper">
                  No services...
                </div>
              )
              : (
                <div>
                  <div className="dependencies__sidebar-category-wrapper">
                    {
                      targetEdges.map(edge => (
                        <div
                          className="dependencies__sidebar-category"
                          key={`${edge.target}`}
                        >
                          <Badge
                            value={edge.target}
                            text={edge.target}
                            onClick={onSearchStringChange}
                          />
                          <div className="dependencies__sidebar-count-wrapper">
                            <div className="dependencies__sidebar-count-label">
                              Count:
                            </div>
                            <div className="dependencies__sidebar-count-value">
                              {edge.metrics.normal}
                            </div>
                            <div className="dependencies__sidebar-count-label">
                              Error:
                            </div>
                            <div className="dependencies__sidebar-count-value">
                              {edge.metrics.danger}
                            </div>
                          </div>
                        </div>
                      ))
                    }
                  </div>
                  <div className="dependencies__sidebar-doughnut-wrapper">
                    <Doughnut edges={targetEdges} renderTarget />
                  </div>
                </div>
              )
            }
          <div className="dependencies__sidebar-category-label">
            <i className="fas fa-angle-double-up" />
            &nbsp;
            Used by (Traced Requests)
          </div>
          {
            sourceEdges.length === 0
              ? (
                <div className="dependencies__sidebar-category-wrapper">
                  No services...
                </div>
              )
              : (
                <div>
                  <div className="dependencies__sidebar-category-wrapper">
                    {
                      sourceEdges.map(edge => (
                        <div
                          className="dependencies__sidebar-category"
                          key={`${edge.source}`}
                        >
                          <Badge
                            value={edge.source}
                            text={edge.source}
                            onClick={onSearchStringChange}
                          />
                          <div className="dependencies__sidebar-count-wrapper">
                            <div className="dependencies__sidebar-count-label">
                              Count:
                            </div>
                            <div className="dependencies__sidebar-count-value">
                              {edge.metrics.normal}
                            </div>
                            <div className="dependencies__sidebar-count-label">
                              Error:
                            </div>
                            <div className="dependencies__sidebar-count-value">
                              {edge.metrics.danger}
                            </div>
                          </div>
                        </div>
                      ))
                    }
                  </div>
                  <div className="dependencies__sidebar-doughnut-wrapper">
                    <Doughnut edges={sourceEdges} renderTarget={false} />
                  </div>
                </div>
              )
            }
        </div>
      </nav>
    </CSSTransition>
  );
};

Sidebar.propTypes = propTypes;

export default Sidebar;
