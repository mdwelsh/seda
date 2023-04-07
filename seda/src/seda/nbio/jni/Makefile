
NODOCS = true

OTHER_TARGETS = build-here
OTHER_CLEAN = clean-here

###### don't modify anything below this line!
BASE = $(shell pwd | sed 's@\(.*\)/seda.*$$@\1/seda@' )
include $(BASE)/Makefile.include

build-here: Makefile.here my-build-here

Makefile.here: configure Makefile.here.in
	./configure

configure: configure.in
	autoconf

my-build-here:
	$(MAKE) -f Makefile.here all

clean-here: 
	if [ -f Makefile.here ]; then $(MAKE) -f Makefile.here clean; fi
	@rm -f Makefile.here config.cache config.status config.log
