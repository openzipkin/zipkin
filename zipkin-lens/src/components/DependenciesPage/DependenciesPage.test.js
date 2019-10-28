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
import React from 'react';
import { cleanup, fireEvent } from '@testing-library/react';

import { DependenciesPageImpl } from './DependenciesPage';
import render from '../../test/util/render-with-default-settings';

jest.mock('./VizceralExt', () => jest.fn(({ objectHighlighted }) => (
  <div>
    <button
      type="button"
      data-testid="graph-background"
      onClick={() => objectHighlighted(undefined)}
    />
    {
      ['serviceA', 'serviceB', 'serviceC', 'serviceD', 'serviceE', 'serviceF', 'serviceG'].map(nodeName => (
        <button
          key={nodeName}
          type="button"
          data-testid={`${nodeName}-button`}
          onClick={() => objectHighlighted({ type: 'node', getName: () => nodeName })}
        >
          {nodeName}
        </button>
      ))
    }
  </div>
)));

describe('<DependenciesPage />', () => {
  const exampleDependencies = [
    { parent: 'serviceA', child: 'serviceB', callCount: 1000 },
    { parent: 'serviceB', child: 'serviceC', callCount: 3200 },
    { parent: 'serviceB', child: 'serviceD', callCount: 200 },
    { parent: 'serviceD', child: 'serviceE', callCount: 500 },
    { parent: 'serviceE', child: 'serviceF', callCount: 1000 },
    { parent: 'serviceC', child: 'serviceE', callCount: 2000 },
    { parent: 'serviceC', child: 'serviceG', callCount: 1200 },
  ];

  const commonProps = {
    isLoading: false,
    dependencies: [],
    fetchDependencies: () => {},
    clearDependencies: () => {},
    location: {
      search: '?endTs=1571979713227&startTs=1571893313227',
    },
    history: { push: () => {} },
  };

  afterEach(() => {
    cleanup();
  });

  it('should render a loading indicator when isLoading is true', () => {
    const { queryAllByTestId } = render(<DependenciesPageImpl {...commonProps} isLoading />);
    const loadingIndicators = queryAllByTestId('loading-indicator');
    expect(loadingIndicators.length).toBe(1);
  });

  it('should render an explain box if there is no graph, yet', () => {
    const { queryAllByTestId } = render(
      <DependenciesPageImpl {...commonProps} dependencies={[]} />,
    );
    const explainBoxes = queryAllByTestId('explain-box');
    expect(explainBoxes.length).toBe(1);
  });

  it('should render a dependency graph when isLoading is false and there is graph data', () => {
    const { queryAllByTestId } = render(
      <DependenciesPageImpl
        {...commonProps}
        dependencies={exampleDependencies}
      />,
    );
    const dependenciesGraphs = queryAllByTestId('dependencies-graph');
    expect(dependenciesGraphs.length).toBe(1);
  });

  it('should render node details when nodes are clicked', () => {
    const { getByTestId, queryAllByTestId } = render(
      <DependenciesPageImpl
        {...commonProps}
        dependencies={exampleDependencies}
      />,
    );
    const serviceA = getByTestId('serviceA-button');
    fireEvent.click(serviceA);
    let nodeDetails = queryAllByTestId('node-detail');
    expect(nodeDetails.length).toBe(1);

    // When the graph's background is clicked, the node detail should be removed.
    const graphBackground = getByTestId('graph-background');
    fireEvent.click(graphBackground);
    nodeDetails = queryAllByTestId('node-detail');
    expect(nodeDetails.length).toBe(0);
  });

  it('should fetch dependencies when mounted using query parameters', () => {
    const fetchDependencies = jest.fn();
    render(
      <DependenciesPageImpl
        {...commonProps}
        fetchDependencies={fetchDependencies}
      />,
    );
    expect(fetchDependencies.mock.calls.length).toBe(1);
  });

  it('should not fetch dependencies when query parameters are missing', () => {
    const fetchDependencies = jest.fn();
    render(
      <DependenciesPageImpl
        {...commonProps}
        fetchDependencies={fetchDependencies}
        location={{ search: '' }}
      />,
    );
    expect(fetchDependencies.mock.calls.length).toBe(0);
  });

  it('should clear dependencies when unmounted', () => {
    const clearDependencies = jest.fn();
    const { unmount } = render(
      <DependenciesPageImpl
        {...commonProps}
        clearDependencies={clearDependencies}
      />,
    );
    unmount();
    expect(clearDependencies.mock.calls.length).toBe(1);
  });
});
