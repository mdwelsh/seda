#!/usr/bin/perl

while (<>) {
  if (/\d+\s+(\S+) megabits/) {
    $rate = $1;
    print "$rate\n";
  }
}

