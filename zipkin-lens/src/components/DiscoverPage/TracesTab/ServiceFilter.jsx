/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import PropTypes from 'prop-types';
import React, { useState, useMemo, useCallback } from 'react';
import { makeStyles } from '@material-ui/styles';
import Badge from '@material-ui/core/Badge';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import Popover from '@material-ui/core/Popover';
import ReactSelect from 'react-select';

import ServiceBadge from '../../Common/ServiceBadge';

const propTypes = {
  filters: PropTypes.arrayOf(PropTypes.string).isRequired,
  addFilter: PropTypes.func.isRequired,
  deleteFilter: PropTypes.func.isRequired,
  allServiceNames: PropTypes.arrayOf(PropTypes.string).isRequired,
};

const useStyles = makeStyles({
  popover: {
    overflow: 'visible',
    padding: '0.2rem',
  },
});

const reactSelectStyles = {
  control: base => ({
    ...base,
    width: '18rem',
    cursor: 'pointer',
    borderRadius: 0,
  }),
};

const ServiceFilter = ({
  filters,
  addFilter,
  deleteFilter,
  allServiceNames,
}) => {
  const classes = useStyles();

  const [menuAnchor, setMenuAnchor] = useState(null);

  const handleButtonClick = event => setMenuAnchor(event.currentTarget);

  const handleMenuClose = () => setMenuAnchor(null);

  const options = useMemo(() => allServiceNames
    .filter(serviceName => !filters.includes(serviceName))
    .map(serviceName => ({
      value: serviceName,
      label: serviceName,
    })), [allServiceNames, filters]);

  const handleMenuChange = useCallback(
    (selected) => {
      const filter = selected.value;
      addFilter(filter);
    },
    [addFilter],
  );

  return (
    <React.Fragment>
      <Badge
        color="secondary"
        badgeContent={`+${filters.length - 1}`}
        invisible={filters.length <= 1}
      >
        <Button onClick={handleButtonClick}>
          <Box component="span" className="fas fa-filter" />
          &nbsp;
          {`${filters.length === 0 ? 'Filter' : filters[0]}`}
        </Button>
      </Badge>
      <Popover
        open={Boolean(menuAnchor)}
        anchorEl={menuAnchor}
        onClose={handleMenuClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'left',
        }}
        classes={{
          paper: classes.popover,
        }}
      >
        <Box
          p={1}
          fontSize="1.1rem"
          borderColor="grey.300"
          borderBottom={1}
        >
          Filters
        </Box>
        {
          filters.length > 0
            ? (
              <Box p={1} display="flex" flexWrap="wrap">
                {
                  filters.map(filter => (
                    <Box m={0.1}>
                      <ServiceBadge
                        serviceName={filter}
                        onClick={() => deleteFilter(filter)}
                      />
                    </Box>
                  ))
                }
              </Box>
            )
            : null
        }
        <ReactSelect
          value={null}
          options={options}
          styles={reactSelectStyles}
          onChange={handleMenuChange}
        />
      </Popover>
    </React.Fragment>
  );
};

ServiceFilter.propTypes = propTypes;

export default ServiceFilter;
