#!/usr/bin/env python
import subprocess

COUNTER = 0
PATH = "https://raw.github.com/twitter/zipkin/master/doc/%s.md"
SRC_LIST = [
  ["overview", "index"],
  ["architecture", None],
  ["install", None],
  ["hadoop", None],
  ["instrument", None]
]

def get_next():
  global COUNTER
  c = COUNTER
  COUNTER += 1
  return c

def run(cmd):
  subprocess.call(cmd, shell=True)

def generate_html(src_file, out_file):
  tmp_file = "tmp_%d" % get_next()
  run("redcarpet --parse-fenced_code_blocks %s > %s" % (src_file, tmp_file))
  run("cat src/head %s src/tail > %s" % (tmp_file, out_file))
  run("rm %s" % tmp_file)

class tmp_file:
  def __enter__(self):
    self.name = "tmp_%d" % get_next()
    return self.name

  def __exit__(self, type, value, traceback):
    run("rm %s" % self.name)

def main():
  for src, override in SRC_LIST:
    url = PATH % src
    with tmp_file() as tmp:
      run("curl -s %s -o %s" % (url, tmp))
      if override is None:
        out_file = "%s.html" % src
      else:
        out_file = "%s.html" % override
      generate_html(tmp, out_file)

if __name__ == "__main__":
  main()

