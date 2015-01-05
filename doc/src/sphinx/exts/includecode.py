import os
import codecs
from os import path

from docutils import nodes
from docutils.parsers.rst import Directive, directives

class IncludeCode(Directive):
    """
    Include a code example from a file with sections delimited with special comments.
    """

    has_content = False
    required_arguments = 1
    optional_arguments = 0
    final_argument_whitespace = False
    option_spec = {
        'section':      directives.unchanged_required,
        'comment':      directives.unchanged_required,
        'marker':       directives.unchanged_required,
        'include':      directives.unchanged_required,
        'exclude':      directives.unchanged_required,
        'hideexcludes': directives.flag,
        'linenos':      directives.flag,
        'language':     directives.unchanged_required,
        'encoding':     directives.encoding,
        'prepend':      directives.unchanged_required,
        'append':       directives.unchanged_required,
    }

    def run(self):
        document = self.state.document
        arg0 = self.arguments[0]
        (filename, sep, section) = arg0.partition('#')

        if not document.settings.file_insertion_enabled:
            return [document.reporter.warning('File insertion disabled',
                                              line=self.lineno)]
        env = document.settings.env
        if filename.startswith('/') or filename.startswith(os.sep):
            rel_fn = filename[1:]
        else:
            docdir = path.dirname(env.doc2path(env.docname, base=None))
            rel_fn = path.join(docdir, filename)
        try:
            fn = path.join(env.srcdir, rel_fn)
        except UnicodeDecodeError:
            # the source directory is a bytestring with non-ASCII characters;
            # let's try to encode the rel_fn in the file system encoding
            rel_fn = rel_fn.encode(sys.getfilesystemencoding())
            fn = path.join(env.srcdir, rel_fn)

        encoding = self.options.get('encoding', env.config.source_encoding)
        codec_info = codecs.lookup(encoding)
        try:
            f = codecs.StreamReaderWriter(open(fn, 'U'),
                    codec_info[2], codec_info[3], 'strict')
            lines = f.readlines()
            f.close()
        except (IOError, OSError):
            return [document.reporter.warning(
                'Include file %r not found or reading it failed' % filename,
                line=self.lineno)]
        except UnicodeError:
            return [document.reporter.warning(
                'Encoding %r used for reading included file %r seems to '
                'be wrong, try giving an :encoding: option' %
                (encoding, filename))]

        comment = self.options.get('comment', '//')
        marker = self.options.get('marker', comment + '#')
        lenm = len(marker)
        if not section:
            section = self.options.get('section')
        include_sections = self.options.get('include', '')
        exclude_sections = self.options.get('exclude', '')
        include = set(include_sections.split(',')) if include_sections else set()
        exclude = set(exclude_sections.split(',')) if exclude_sections else set()
        hideexcludes = 'hideexcludes' in self.options
        if section:
            include |= set([section])
        within = set()
        res = []
        excluding = False
        for line in lines:
            index = line.find(marker)
            if index >= 0:
                section_name = line[index+lenm:].strip()
                if section_name in within:
                    within ^= set([section_name])
                    if excluding and not (exclude & within):
                        excluding = False
                else:
                    within |= set([section_name])
                    if not excluding and (exclude & within):
                        excluding = True
                        if not hideexcludes:
                            res.append(' ' * index + comment + ' ' + section_name.replace('-', ' ') + ' ...\n')
            elif not (exclude & within) and (not include or (include & within)):
                res.append(line)
        lines = res

        def countwhile(predicate, iterable):
            count = 0
            for x in iterable:
                if predicate(x):
                    count += 1
                else:
                    return count

        nonempty = filter(lambda l: l.strip(), lines)
        tabcounts = map(lambda l: countwhile(lambda c: c == ' ', l), nonempty)
        tabshift = min(tabcounts) if tabcounts else 0

        if tabshift > 0:
            lines = map(lambda l: l[tabshift:] if len(l) > tabshift else l, lines)

        prepend = self.options.get('prepend')
        append  = self.options.get('append')
        if prepend:
           lines.insert(0, prepend + '\n')
        if append:
           lines.append(append + '\n')

        text = ''.join(lines)
        retnode = nodes.literal_block(text, text, source=fn)
        retnode.line = 1
        retnode.attributes['line_number'] = self.lineno
        language = self.options.get('language')
        if language:
            retnode['language'] = language
        if 'linenos' in self.options:
            retnode['linenos'] = True
        document.settings.env.note_dependency(rel_fn)
        return [retnode]

def setup(app):
    app.require_sphinx('1.0')
    app.add_directive('includecode', IncludeCode)
