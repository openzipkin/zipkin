import re

def find_release(file):
  """
  Attempt to find the library version in an SBT build file.

  Note that the use of a variable named 'libVersion' is only a convention.
  """
  try:
    f = open(file, 'r')
    for line in f:
      m = re.search('libVersion\s*=\s*"(\d+\.\d+\.\d+)"', line)
      if m is not None:
        return m.group(1)
    return ''
  except (OSError, IOError):
    return ''

def release_to_version(release):
  """Extract the 'version' from the full release string."""
  m = re.search('(\d+\.\d+)\.\d+', release)
  return '' if m is None else m.group(1)

