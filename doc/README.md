Zipkin Website
===================
The [zipkin website](http://openzipkin.github.io/zipkin/) is running off the [gh-pages](https://github.com/openzipkin/zipkin/tree/gh-pages) branch of the zipkin repository.

### Setup
The source of the website is in the `doc` folder, and built with Sphinx. You'll need to install that.

```bash
$ pip install -U Sphinx
```

### Building the docs
You'll want to run sphinx and point it at checkout of [gh-pages](https://github.com/openzipkin/zipkin/tree/gh-pages).

```bash
sphinx-build -b html doc/src/sphinx /path/to/gh-pages
```

