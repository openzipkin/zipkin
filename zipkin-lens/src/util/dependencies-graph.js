class Graph {
  constructor(rawDependencies) {
    this.nodes = [];
    this.edges = [];

    rawDependencies.forEach(
      edge => this.addEdge(edge),
    );
  }

  addEdge(edge) {
    if (!this.allNodeNames().includes(edge.parent)) {
      this.nodes.push({
        name: edge.parent,
      });
    }
    if (!this.allNodeNames().includes(edge.child)) {
      this.nodes.push({
        name: edge.child,
      });
    }
    this.edges.push({
      source: edge.parent,
      target: edge.child,
      metrics: {
        normal: edge.callCount || 0,
        danger: edge.errorCount || 0,
      },
    });
  }

  allNodeNames() {
    return this.nodes.map(node => node.name);
  }

  allNodes() {
    return this.nodes;
  }

  allEdges() {
    return this.edges;
  }

  getTargetEdges(serviceName) {
    return this.allEdges().filter(edge => edge.source === serviceName);
  }

  getSourceEdges(serviceName) {
    return this.allEdges().filter(edge => edge.target === serviceName);
  }
}

export default Graph;
