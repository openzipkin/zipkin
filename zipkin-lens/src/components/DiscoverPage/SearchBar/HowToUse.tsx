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

import { Typography } from '@material-ui/core';
import React from 'react';
import styled from 'styled-components';

const Root = styled.div`
  position: absolute;
  top: 45px;
  left: 0px;
  right: 0px;
  border-radius: 1px;
  background-color: ${({ theme }) => theme.palette.background.paper};
  box-shadow: ${({ theme }) => theme.shadows[3]};
  max-height: 300px;
  overflow: auto;
  z-index: ${({ theme }) => theme.zIndex.modal};
  padding: ${({ theme }) => theme.spacing(1)}px;
`;

const Code = styled.code`
  padding: 2px;
  background-color: ${({ theme }) => theme.palette.grey[100]};
  border: 1px solid ${({ theme }) => theme.palette.grey[300]};
  border-radius: 3px;
  box-sizing: border-box;
  display: inline-block;
  font-family: monospace;
`;

const ExampleList = styled.ul`
  margin-block-start: ${({ theme }) => theme.spacing(0.5)}px;
  margin-block-end: ${({ theme }) => theme.spacing(0.5)}px;
`;

const ExampleListItem = styled.li`
  padding-top: 2px;
  padding-bottom: 2px;
`;

interface HowToUseProps {
  target: 'minDuration' | 'maxDuration' | 'tagQuery';
}

const HowToUse: React.FC<HowToUseProps> = ({ target }) => {
  let content;

  switch (target) {
    case 'minDuration':
    case 'maxDuration':
      content = (
        <>
          <Typography variant="h6">Examples</Typography>
          <ExampleList>
            <ExampleListItem>
              <Code>{target}=10s</Code>
              &nbsp;(Seconds)
            </ExampleListItem>
            <ExampleListItem>
              <Code>{target}=10ms</Code>
              &nbsp;(Milliseconds)
            </ExampleListItem>
            <ExampleListItem>
              <Code>{target}=10us</Code>
              &nbsp;(Microseconds)
            </ExampleListItem>
          </ExampleList>
          If no units are specified, the number is treated as <Code>us</Code>.
          <Code>{target}=10</Code> is the same as <Code>{target}=10us</Code>.
        </>
      );
      break;
    case 'tagQuery':
      content = (
        <>
          Limit results to traces that include one or more tags. Tags are only
          searchable by their exact value. Use <Code> and </Code> to combine
          terms.
          <Code> or </Code> is not supported.
          <Typography variant="h6">Example</Typography>
          <ExampleList>
            <ExampleListItem>
              <Code>tagQuery=error and http.method=POST</Code>
            </ExampleListItem>
          </ExampleList>
        </>
      );
      break;
    default:
      break;
  }

  return <Root>{content}</Root>;
};

export default HowToUse;
