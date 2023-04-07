#!/usr/bin/perl

for ($i = 0; $i <= $#ARGV; $i++) {

  $fname = $ARGV[$i];
  $lat = ".$i.lat";
  $rate = ".$i.rate";

  open (FILE, $fname) || die "Can't open $fname\n";
#  open (LAT, ">$lat") || die "Can't open $lat\n";
  open (RATE, ">$rate") || die "Can't open $rate\n";

  while (<FILE>) {
#    if (/Latency/) { print LAT; }
    if (/Overall/) { print RATE; }
  }
  close (FILE);
  close (LAT);
  close (RATE);

}

open (GRAPH, ">.GRAPH") || die "Can't open .GRAPH";
print GRAPH "set xlabel 'Time'\n";
#print GRAPH "set ylabel 'Latency in msec'\n";
print GRAPH "set y2label 'Completions/sec'\n";
print GRAPH "set y2tics 10\n";

print GRAPH "plot \\\n";

for ($i = 0; $i <= $#ARGV; $i++) {
  $fname = $ARGV[$i];
  $lat = ".$i.lat";
  $rate = ".$i.rate";
#  print GRAPH "  \"$lat\" using 2 with lines, \\\n";
#  print GRAPH "  \"$rate\" using 3 axes x1y2 with linesp";
#  print GRAPH "  \"$lat\" using 3 with linesp, \\\n";
  print GRAPH "  \"$rate\" using 4 axes x1y2 with linesp";
  if ($i != $#ARGV) {
    print GRAPH ",\\\n";
  } else {
    print GRAPH "\n";
  } 
}

print GRAPH "pause -1 'Press return to quit'\n";
close (GRAPH);

`gnuplot .GRAPH`;

print STDERR "Cleaning up...\n";
`rm .GRAPH`;
for ($i = 0; $i <= $#ARGV; $i++) {
  $fname = $ARGV[$i];
#  $lat = ".$i.lat";
  $rate = ".$i.rate";

#  `rm $lat`;
  `rm $rate`;
}

