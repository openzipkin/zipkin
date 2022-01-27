# Zipkin Helm Chart

## Usage

[Helm](https://helm.sh) must be installed to use the charts.
Please refer to Helm's [documentation](https://helm.sh/docs/) to get started.

Once Helm is set up properly, add the repo as follows:

```console
helm repo add openzipkin https://openzipkin.github.io/zipkin
```

You can then run `helm search repo openzipkin` to see the charts.

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| affinity | object | `{}` |  |
| autoscaling.enabled | bool | `false` |  |
| autoscaling.maxReplicas | int | `100` |  |
| autoscaling.minReplicas | int | `1` |  |
| autoscaling.targetCPUUtilizationPercentage | int | `80` |  |
| fullnameOverride | string | `""` |  |
| image.pullPolicy | string | `"IfNotPresent"` |  |
| image.repository | string | `"openzipkin/zipkin-slim"` |  |
| image.tag | string | `""` |  |
| imagePullSecrets | list | `[]` |  |
| ingress.annotations | object | `{}` |  |
| ingress.enabled | bool | `false` |  |
| ingress.host | string | `"chart-example.local"` |  kubernetes.io/tls-acme: "true" className: nginx |
| ingress.path | string | `"/"` |  |
| ingress.tls | list | `[]` |  |
| nameOverride | string | `""` |  |
| nodeSelector | object | `{}` |  |
| podAnnotations."sidecar.istio.io/inject" | string | `"false"` |  |
| podSecurityContext | object | `{}` |  |
| replicaCount | int | `1` |  |
| resources.limits | object | `{"cpu":"500m","memory":"4096Mi"}` |  choice for the user. This also increases chances charts run on environments with little resources, such as Minikube. If you do want to specify resources, uncomment the following lines, adjust them as necessary, and remove the curly braces after 'resources:'. limits:   cpu: 100m   memory: 128Mi requests:   cpu: 100m   memory: 128Mi |
| resources.requests.cpu | string | `"100m"` |  |
| resources.requests.memory | string | `"128Mi"` |  |
| securityContext.readOnlyRootFilesystem | bool | `true` |    drop:   - ALL |
| securityContext.runAsNonRoot | bool | `true` |  |
| securityContext.runAsUser | int | `1000` |  |
| service.port | int | `9411` |  |
| service.type | string | `"ClusterIP"` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `true` |  |
| serviceAccount.name | string | `""` |  If not set and create is true, a name is generated using the fullname template |
| serviceAccount.psp | bool | `false` |  |
| tolerations | list | `[]` |  |
| zipkin.storage.elasticsearch.hosts | string | `"hostA hostB"` |  |
| zipkin.storage.elasticsearch.index | string | `"fooIndex"` |  |
| zipkin.storage.type | string | `"elasticsearch"` |  |

The values are validated using a JSON schema, which contains logic to enforce either:

- `zipkin.storage.type` is set to `mem`
- `zipkin.storage.type` is set to `elasticsearch` *AND* both `z.s.elasticsearch.hosts` and `z.s.elasticsearch.index` is set
