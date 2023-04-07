#!/usr/bin/perl

# Script to automate running the Pingpong benchmark for multiple
# packet sizes

$i = 4;
$MAX_SIZE = 8192;

while ($i <= $MAX_SIZE) {
  print "$i\n";

  $out = `(cd ../p2p-bench; java Pingpong recv foo $i)`;

  print $out;
  $i+=16;
}
