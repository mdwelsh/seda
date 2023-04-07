#!/usr/bin/perl

# haboobstats.pl - extract statistics from Haboob server from a log file.
#
# Matt Welsh, mdw@cs.berkeley.edu

$IN_INITIAL = 0;
$IN_FINAL = 0;

while (<>) {
  if (/^\# Initial Haboob/) {
    if ($IN_INITIAL) { $IN_INITIAL = 0; }
    else { $IN_INITIAL = 1; }
  }
  if (/^\# Ending Haboob/) {
    if ($IN_FINAL) { $IN_FINAL = 0; }
    else { $IN_FINAL = 1; }
  }

  if (/^<br>Total requests: (\d+)/) {
    if ($IN_INITIAL) {
      $initial_reqs = $1;
    } elsif ($IN_FINAL) {
      $final_reqs = $1;
    }
  }

  if (/^<br>Cache hits: (\d+)/) {
    if ($IN_INITIAL) {
      $initial_hits = $1;
    } elsif ($IN_FINAL) {
      $final_hits = $1;
    }
  }

  if (/^<br>Cache misses: (\d+)/) {
    if ($IN_INITIAL) {
      $initial_misses = $1;
    } elsif ($IN_FINAL) {
      $final_misses = $1;
    }
  }

  if (/^<br>Total memory in use: (\S+)/) {
    if ($IN_INITIAL) {
      $initial_totalkb = $1;
    } elsif ($IN_FINAL) {
      $final_totalkb = $1;
    }
  }

  if (/^<br>Free memory: (\S+)/) {
    if ($IN_INITIAL) {
      $initial_freekb = $1;
    } elsif ($IN_FINAL) {
      $final_freekb = $1;
    }
  }

  if (/^<br>Total connections: (\S+)/) {
    if ($IN_INITIAL) {
      $initial_connections = $1;
    } elsif ($IN_FINAL) {
      $final_connections = $1;
    }
  }

  if (/^<br>Current size of page cache: (\S+) files, (\S+)/) {
    if ($IN_INITIAL) {
      $initial_cachefiles = $1;
      $initial_cachekb = $2;
    } elsif ($IN_FINAL) {
      $final_cachefiles = $1;
      $final_cachekb = $2;
    }
  }

}


format STDOUT_TOP =
Type              Initial         Final           Net
----------------- --------------- --------------- --------------- 
.

format STDOUT =
Page Requests     @<<<<<<<<<<<<<< @<<<<<<<<<<<<<< @<<<<<<<<<<<<<<
              $initial_reqs,  $final_reqs,    ($final_reqs - $initial_reqs)
Cache Hits        @<<<<<<<<<<<<<< @<<<<<<<<<<<<<< @<<<<<<<<<<<<<<
              $initial_hits,  $final_hits,    ($final_hits - $initial_hits)
Cache Misses      @<<<<<<<<<<<<<< @<<<<<<<<<<<<<< @<<<<<<<<<<<<<<
              $initial_misses,  $final_misses, ($final_misses - $initial_misses)
Memory (KB)       @<<<<<<<<<<<<<< @<<<<<<<<<<<<<< @<<<<<<<<<<<<<<
             $initial_totalkb, $final_totalkb, ($final_totalkb-$initial_totalkb)
Cache size (KB)   @<<<<<<<<<<<<<< @<<<<<<<<<<<<<< @<<<<<<<<<<<<<<
             $initial_cachekb, $final_cachekb, ($final_cachekb-$initial_cachekb)
Cache files       @<<<<<<<<<<<<<< @<<<<<<<<<<<<<< @<<<<<<<<<<<<<<
 $initial_cachefiles, $final_cachefiles, ($final_cachefiles-$initial_cachefiles)
Connections       @<<<<<<<<<<<<<< @<<<<<<<<<<<<<< @<<<<<<<<<<<<<<
 $initial_connections, $final_connections, ($final_connections-$initial_connections)
.

write;
