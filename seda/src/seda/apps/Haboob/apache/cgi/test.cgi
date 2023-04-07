#!/usr/bin/perl

$| = 1;

$out = `uname -a`; chop $out;
print "Content-type: text/plain\n\n";
print "uname -a reports: $out\n";
