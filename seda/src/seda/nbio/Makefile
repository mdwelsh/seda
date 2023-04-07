SUBDIRS = jni test

OTHER_TARGETS = jni-headers 
OTHER_CLEAN = other-clean

###### don't modify anything below this line!
BASE = $(shell pwd | sed 's@\(.*\)/seda.*$$@\1/seda@' )
include $(BASE)/Makefile.include

JNI_HEADERS = $(addprefix jni/,$(CLASSES:.class=.h))
PACKAGE = seda.nbio

jni/%.h: %.class
	javah -jni -o $@ $(PACKAGE).$(<:.class=) 

jni-headers: $(JNI_HEADERS)

other-clean:
	@rm -f $(JNI_HEADERS)
