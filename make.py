#!/usr/bin/env python
import subprocess

COUNTER = 0
SRC_LIST = [
  "index",
  "architecture",
  "install",
  "instrument"
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

def main():
  for src in SRC_LIST:
    src_file = "src/%s.md" % src
    out_file = "%s.html" % src
    generate_html(src_file, out_file)


if __name__ == "__main__":
  main()

