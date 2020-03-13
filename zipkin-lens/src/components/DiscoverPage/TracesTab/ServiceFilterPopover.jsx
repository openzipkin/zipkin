/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import { t, Trans } from '@lingui/macro';
import { useLingui } from '@lingui/react';
import PropTypes from 'prop-types';
import React, { useMemo, useState } from 'react';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Popover from '@material-ui/core/Popover';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import TextField from '@material-ui/core/TextField';
import Typography from '@material-ui/core/Typography';
import grey from '@material-ui/core/colors/grey';

import ServiceBadge from '../../Common/ServiceBadge';

const propTypes = {
  open: PropTypes.bool.isRequired,
  anchorEl: PropTypes.shape({}),
  onClose: PropTypes.func.isRequired,
  filters: PropTypes.arrayOf(PropTypes.string).isRequired,
  allServiceNames: PropTypes.arrayOf(PropTypes.string).isRequired,
  onAddFilter: PropTypes.func.isRequired,
  onDeleteFilter: PropTypes.func.isRequired,
};

const defaultProps = {
  anchorEl: null,
};

const useStyles = makeStyles({
  paper: {
    display: 'flex',
    flexDirection: 'column',
    width: '30rem',
  },
  label: {
    backgroundColor: grey[50],
    padding: '0.45rem',
    borderBottom: `1px solid ${grey[300]}`,
  },
  textField: {
    width: '29rem',
  },
  filters: {
    padding: '0.45rem',
    borderBottom: `1px solid ${grey[300]}`,
  },
  badgeWrapper: {
    margin: '0.3rem',
  },
  serviceList: {
    maxHeight: '30rem',
    overflow: 'auto',
  },
});

const anchorOrigin = {
  vertical: 'bottom',
  horizontal: 'left',
};

const ServiceFilterPopover = ({
  open,
  anchorEl,
  onClose,
  filters,
  allServiceNames,
  onAddFilter,
  onDeleteFilter,
}) => {
  const classes = useStyles();
  const { i18n } = useLingui();

  const [filterText, setFilterText] = useState('');

  const handleTextChange = (e) => setFilterText(e.target.value);

  const filteredServiceNames = useMemo(
    () =>
      allServiceNames
        .filter((serviceName) => !filters.includes(serviceName))
        .filter((serviceName) => serviceName.includes(filterText)),
    [allServiceNames, filters, filterText],
  );

  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={anchorOrigin}
      classes={{ paper: classes.paper }}
      data-testid="service-filter-popover"
    >
      <Box className={classes.label} data-testid="label">
        <Typography variant="h5">
          <Trans>Filter</Trans>
        </Typography>
      </Box>
      <Box display="flex" justifyContent="center">
        <TextField
          value={filterText}
          label={i18n._(t`Service Name`)}
          className={classes.textField}
          onChange={handleTextChange}
          data-testid="text-field"
        />
      </Box>
      {filters.length > 0 ? (
        <Box className={classes.filters} data-testid="filters">
          {filters.map((filter) => (
            <Box className={classes.badgeWrapper} key={filter}>
              <ServiceBadge
                serviceName={filter}
                onDelete={() => onDeleteFilter(filter)}
              />
            </Box>
          ))}
        </Box>
      ) : null}
      <List className={classes.serviceList}>
        {filteredServiceNames.map((serviceName) => (
          <ListItem
            button
            onClick={() => onAddFilter(serviceName)}
            key={serviceName}
          >
            <ListItemText primary={serviceName} />
          </ListItem>
        ))}
      </List>
    </Popover>
  );
};

ServiceFilterPopover.propTypes = propTypes;
ServiceFilterPopover.defaultProps = defaultProps;

export default ServiceFilterPopover;
