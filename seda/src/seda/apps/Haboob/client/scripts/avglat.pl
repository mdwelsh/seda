#!/usr/bin/perl

# avglat.pl - determines min, max, and average response times for service
# Input is an gexec log file with lines of the form
#   N Latency: L ms
# where 'N' is the node number, and 'L' is the measured latency. 
#
# Matt Welsh <mdw@cs.berkeley.edu>

while (<>) {
  if (/(\d+)\s+Latency:\s+(\S+)/) {
    if (!$min_lat[$1] || ($2 < $min_lat[$1])) {
      $min_lat[$1] = $2;
    }
    if ($2 > $max_lat[$1]) {
      $max_lat[$1] = $2;
    }
    $total_lat[$1] += $2;
    $num_lat_samples[$1]++;
  }
}

$total_min = 1e15;
$total_max = 0;

for ($i = 0; $i <= $#total_lat; $i++) {
  if ($num_lat_samples[$i] != 0) {
    $avg_lat[$i] = $total_lat[$i] / $num_lat_samples[$i];
    print "Node $i had $num_lat_samples[$i] samples, min $min_lat[$i], avg $avg_lat[$i], max $max_lat[$i]\n";
    $thetotal += $avg_lat[$i];
    if ($min_lat[$i] < $total_min) { $total_min = $min_lat[$i]; }
    if ($max_lat[$i] > $total_max) { $total_max = $max_lat[$i]; }
  } else {
    print "Node $i had 0 samples\n";
  }
}

$num_nodes = $#total_lat+1;
$total_avg = $thetotal/$num_nodes;
print "\nMin latency for $num_nodes nodes: $total_min ms\n";
print "Avg latency for $num_nodes nodes: $total_avg ms\n";
print "Max latency for $num_nodes nodes: $total_max ms\n";
