#!/usr/bin/env python2

# Copyright (c) 2001 by The Regents of the University of California. 
# All rights reserved.
#
# Permission to use, copy, modify, and distribute this software and its
# documentation for any purpose, without fee, and without written agreement is
# hereby granted, provided that the above copyright notice and the following
# two paragraphs appear in all copies of this software.
# 
# IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR
# DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING OUT
# OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF THE UNIVERSITY OF
# CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
# 
# THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
# AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
# ON AN "AS IS" BASIS, AND THE UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO
# PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
#
# Author: Eric Wagner <eric@xcf.berkeley.edu>


"""
Pytec is a method to encode python scripts inside of
html documents.  Due to the syntax of python, you are able
to define your own classes, methods and whatnot right in
the template file and have it work as expected.

The syntax for pytec is as follows.  Inside your HTML document,
you can insert <%pytec ...python code here... %> to have
python code inside your document.  Using print works as expected,
with the output going to the client in the http response.

Within the %pytec directive, you are expected to format your
code properly, as in python, whitespace is part of the grammar.
Thus, the first line must start in the first column.  Note that
logic is not extended beyond the directive, so you cannot say
things like

<%pytec
if x > 0:
%>
<h1>X is greater than 0</h1>
<%pytec
else:
%>
<h1>X is less or equal to 0</h1>

You will have to wrap the HTML in print statements yourself.

Variables, however, do live beyond the directive.  That is, you can
say:

<%pytec
my_var = 3
%>

<h1>some random text</h1>
<%pytec
print 'my_var = %s' % my_var
%>

Another directive is the <%= %> directive, which will evaluate the
statement therein and insert it into the html.  This is good for
outputting individual variables like this:

<%pytec
my_tax_rate = .06
cost_of_shirt = 15
%>

<h1>The shirt will cost <%= cost_of_shirt * (1 + my_tax_rate) %></H1>

Last is the <%include %> directive.  It expects a filename argument.
This file is interpreted as a pytec document, but plain html
will work.  Also note that the program will recursively look for
include directives in the file, but avoids circular includes.


After you've written your pytec template, you run this program over
the file:

./pytec.py mytempl.pytec > mytempl.py

After this, you need to compile the python program output into java
bytecode.  You use the jythonc command as follows:

jythonc -p seda.apps.Haboob.pytec -w ~/research/ mytempl.py

where the argument after -p is the package you want this class in, and
the argument after the -w is the root directory where this package
namespace lives.

The output will be a .class file usable by the Haboob web server.

Eric Wagner 8/15/1
eric@xcf.berkeley.edu

"""


import re
import __builtin__

class TranslationError(Exception): pass

def read_page(filename, old_pages=None):
    if not old_pages:
        old_pages = []
    include = re.compile('<%include (.*?)>')
    old_pages.append(filename)

    page = open(filename).read()
    for file in include.findall(page):
        if file not in old_pages:
            page = re.sub('<%%include %s>' % file, read_page(file, old_pages), page)
        else:
            raise TranslationError, 'circular import'

    return page

    
class TemplateTranslator:
    # search strings
    nondirective =      re.compile('(.*?)<%', re.DOTALL)
    directive =         re.compile('(.*?)%>|(:)>', re.DOTALL)
    non_whitespace =    re.compile('\S')
    whitespace =        re.compile('^(\s*)')
    colon =             re.compile(':')
    rest =              re.compile('(.*$)', re.DOTALL)
    include =           re.compile('<%include (.*)>')

    def __init__(self, filename):
        self.filename = filename
        self.page = read_page(filename)
#        self.page = open(filename).read()

    def translate(self):
        curr = 0
        source = []


        while(1):

            ordinary = self.nondirective.search(self.page, curr)

            if not ordinary:
                # no more directives
                ordinary = self.rest.search(self.page, curr)
                plaintext = ordinary.group(1).strip()
                # print 'print """%s""",' % plaintext
                source.append('print >> _pytec_out, """%s""",' % plaintext)
                break # we're done

            if self.non_whitespace.search(ordinary.group(1)):
                plaintext = ordinary.group(1).strip()
                # print 'print """%s""",' % plaintext
                source.append('print >> _pytec_out, """%s""",' % plaintext)

            curr = ordinary.end()

            special = self.directive.search(self.page, curr)
            if not special:
                raise TranslationError

            command = special.group(1)

            if command.startswith('pytec'):
                # this is a regular directive
                # everything after the first \n
                command = command[command.find('\n') + 1:]
                lines = command.split('\n')
                lines = map(
                    lambda x:re.sub('^(\s*)print\s*(?!>)', '\g<1>print >> _pytec_out, ', x),
                    lines
                )
                # print command
                source.extend(lines)

            elif command[0] == '!':
                # this is a directive we have to place with the proper indent
                command = command[1:].strip()

                # print command
                source.extend(command.split('\n'))

            elif command[0] == '=':
                # we print the result of this directive
                # should we check to ensure that this is a single statement?
                command = command[1:].strip()
                # print 'print eval(r"""' + command + '"""),'
                source.append('_pytec_out.softspace = 0')
                source.append('print >> _pytec_out, eval(r"""' + command + '"""),')
                source.append('_pytec_out.softspace = 0')

            else:
                print source
                raise TranslationError

            curr = special.end()

        print 'from __future__ import nested_scopes'
        print 'from seda.sandStorm.core import *'
        print 'from seda.sandStorm.lib.http import *'
        print 'from seda.apps.Haboob.hdapi import *'
        #print 'from java.lang import *'
        print 'import java'
        print 'import StringIO'
        print
        print 'class %s(httpRequestHandlerIF):' % self.filename.split('.')[0]
        print '\tdef handleRequest(self, req):'
        print '\t\t_pytec_out = StringIO.StringIO()'
        for line in source:
            print '\t\t%s' % line
        print '\t\toutstring = "".join(_pytec_out.buflist)'
        print '\t\tresp = httpOKResponse("text/html", BufferElement(outstring))'
        print '\t\treturn resp'
        print
        print 'def error(msg):'
        print '\tresp = httpOKResponse("text/html", BufferElement(msg))'
        print '\treturn resp'

if __name__ == '__main__':
    import sys
    if not sys.argv[1]:
        print "Must provide filename"
        sys.exit()

    tt = TemplateTranslator(sys.argv[1])
    tt.translate()

    


