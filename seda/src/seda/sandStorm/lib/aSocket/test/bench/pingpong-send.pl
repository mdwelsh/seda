#!/usr/bin/perl

# Script to automate running the Pingpong benchmark for multiple
# packet sizes

$i = 4;
$MAX_SIZE = 4095;
$HOST = "mm55";

$hostname = `hostname`; chop $hostname;
$date = `date`; chop $date;

print "# Pingpong test results\n";
print "# Sender: $hostname Receiver: $HOST\n";
print "# Date: $date\n";
print "# Message size from $i to $MAX_SIZE\n";
print "#\n";

while ($i <= $MAX_SIZE) {

  $out = `cd ../p2p-bench; java Pingpong send $HOST $i`;

  print $out;
  $i+=16;
  select(undef, undef, undef, 0.5);
}
