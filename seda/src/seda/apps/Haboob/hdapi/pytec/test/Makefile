# This rather ugly Makefile can be used to compile PyTeC source files to
# a .jar file that can be executed by Haboob through HDAPI.

BASE = $(shell pwd | sed 's@\(.*\)/seda.*$$@\1/seda@' )
include $(BASE)/Makefile.include

# Base directory for PyTeC
PYTEC_DIR=..

# Name of the PyTeC files you want to compile
PYTEC_FILES = test.pytec
# Package name the resulting classes should be in
PACKAGE = seda.apps.Haboob.hdapi.pytec.test

## YOU SHOULD NOT NEED TO EDIT ANYTHING BELOW HERE #######################

PACKAGE_BASE = $(firstword $(subst ., ,$(PACKAGE)))
JARFILE = pytec-compiled.jar
TMPDIR=/tmp/jythonwork
PY_FILES = $(PYTEC_FILES:.pytec=.py)
CLASS_FILES = $(PY_FILES:.py=.class)

.PRECIOUS: $(PY_FILES)

all: $(CLASS_FILES) $(JARFILE)

%.py: %.pytec
	$(PYTEC_DIR)/pytec.py $< > $@

%.class: %.py
	jythonc -p $(PACKAGE) -w $(TMPDIR) $<

$(JARFILE): $(CLASS_FILES)
	jar cvf $(JARFILE) $(PACKAGE_BASE)
	rm -rf $(PACKAGE_BASE) $(TMPDIR)

clean:
	rm -rf $(PY_FILES) $(CLASS_FILES) $(JARFILE) $(PACKAGE_BASE)

