######################################################################
# Makefile from Rob von Behren, from pieces stolen from Steve Gribble
# and Matt Welsh
#
# ***new package creators:  you must modify the following variables..***
#
#    SUBDIRS = list of subdirectories to recurse
# 
######################################################################

SUBDIRS = 
NODOCS = true

###### don't modify anything below this line!
BASE = $(shell pwd | sed 's@\(.*\)/seda.*$$@\1/seda@' )
include $(BASE)/Makefile.include

run:
	LD_LIBRARY_PATH=$LD_LIBRARY_PATH:../../jni java NBIOTest
