#
# Copyright The OpenZipkin Authors
# SPDX-License-Identifier: Apache-2.0
#

# Use latest version here: https://github.com/orgs/openzipkin/packages/container/package/alpine
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG alpine_version=3.20.0

# java_version is used during the installation process to build or download the zipkin-lens jar.
#
# Use latest version here: https://github.com/orgs/openzipkin/packages/container/package/java
# This is defined in many places because Docker has no "env" script functionality unless you use
# docker-compose: When updating, update everywhere.
ARG java_version=21.0.3_p9

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY build-bin/ /build-bin/
COPY build-bin/docker/docker-healthcheck /docker-bin/
COPY docker/test-images/zipkin-uiproxy/start-nginx /docker-bin/
COPY docker/test-images/zipkin-uiproxy/nginx.conf /conf.d/zipkin.conf.template

FROM ghcr.io/openzipkin/alpine:$alpine_version as zipkin-uiproxy
LABEL org.opencontainers.image.description="NGINX on Alpine Linux proxying the Zipkin UI with proxy_pass"
# Use latest from https://pkgs.alpinelinux.org/packages?name=nginx
ARG nginx_version=1.26.0
LABEL nginx-version=$nginx_version

ENV ZIPKIN_UI_BASEPATH=/zipkin
ENV ZIPKIN_BASE_URL=http://zipkin:9411

# Add HEALTHCHECK and ENTRYPOINT scripts into the default search path
COPY --from=scratch /docker-bin/* /usr/local/bin/
# We use start period of 30s to avoid marking the container unhealthy on slow or contended CI hosts
HEALTHCHECK --interval=1s --start-period=30s --timeout=5s CMD ["docker-healthcheck"]
ENTRYPOINT ["start-nginx"]

# Setup NGINX
COPY --from=scratch /conf.d/ /etc/nginx/conf.d/
RUN apk add --update --no-cache nginx=~${nginx_version} && \
    mkdir -p /var/tmp/nginx && chown -R nginx:nginx /var/tmp/nginx

# Usually, we read env set from pid 1 to get docker-healthcheck parameters. However, NGINX wipes the
# env, so we need to expose it in the Dockerfile instead.
ENV HEALTHCHECK_PORT=80
EXPOSE 80
