Quickstart
==========

In this section we'll walk through building and starting an instance of Zipkin
and how to enable tracing on your service.

Docker
------
If you are familiar with Docker, the quickest way to get started quickly is to
use the `Docker Zipkin`_ project, which (in addition to being able to build docker
images) provides scripts and a `docker-compose.yml`_ for launching pre-built images,
e.g.

.. parsed-literal::
    $ git clone https://github.com/openzipkin/docker-zipkin
    $ cd docker-zipkin
    $ docker-compose up


Running from Source
-------------------
Zipkin can be run from source, if you are testing new features or cannot use docker.
These instructions are the first thing you see in the `Zipkin`_ GitHub repository.

.. _Docker Zipkin: https://github.com/openzipkin/docker-zipkin
.. _Zipkin: https://github.com/openzipkin/zipkin
.. _docker-compose.yml: https://github.com/openzipkin/docker-zipkin/blob/master/docker-compose.yml
