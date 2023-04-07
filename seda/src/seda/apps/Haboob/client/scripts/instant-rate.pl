#!/usr/bin/perl

# instant-rate.pl - determine *instantaneous* completion rate and 
# bandwidth for a benchmark run. Looks at bandwidth across all 
# "simultaneous" samples emitted by clients (with the caveat that 
# the samples may not be correlated in time).
# 
# Input is a gexec log file with lines of the form:
#   N Overall rate: R 
#   N Bandwidth: B
# where 'N' is the node number, 'R' is a completion rate (in completions
# per second), and 'B' is a bandwidth (in bytes per sec).
#
# Matt Welsh, mdw@cs.berkeley.edu

# Skip this many samples at the beginning of a run
$SKIP_SAMPLES = 3;

# Maximum number of samples to consider
$MAX_SAMPLES = 100;

while (<>) {
  if (/(\d+)\s+Overall rate:\s+(\S+)/) {
    if ($num_rate_samples[$1] < $MAX_SAMPLES) {
      if ($num_rate_samples[$1] >= $SKIP_SAMPLES) {
        $sample_num = $num_rate_samples[$1];
        $total_rate[$sample_num] += $2;
	$num_clients[$sample_num]++;
      }
      $num_rate_samples[$1]++;
    }
  }
  if (/(\d+)\s+Bandwidth:\s+(\S+)/) {
    if ($num_bw_samples[$1] < $MAX_SAMPLES) {
      if ($num_bw_samples[$1] >= $SKIP_SAMPLES) {
        $sample_num = $num_bw_samples[$1];
        $total_bw[$sample_num] += $2;
      }
      $num_bw_samples[$1]++;
    }
  }
}

for ($i = 0; $i <= $#total_rate; $i++) {
  if ($i > $SKIP_SAMPLES) {
    $avg_rate[$i] = $total_rate[$i] / $num_clients[$i];
    print "Sample $i: $total_rate[$i] comps/sec, $num_clients[$i] clients\n";
    $thetotal += $total_rate[$i];
  }
}

$num_samples = $#total_rate+1;
$thetotal /= $num_samples;
print "\nTotal rate for $num_samples samples: $thetotal comps/sec\n\n";

#for ($i = 0; $i <= $#total_bw; $i++) {
#  if (($num_bw_samples[$i] - $SKIP_SAMPLES) > 0) {
#    $num = $num_bw_samples[$i] - $SKIP_SAMPLES;
#    $avg_bw[$i] = $total_bw[$i] / $num;
#    print "Node $i had $num_bw_samples[$i] samples, avg $avg_bw[$i] bytes/sec\n";
#    $thetotal += $avg_bw[$i];
#
#  } else {
#    print "Node $i had 0 samples\n";
#  }
#}
#
#$num_nodes = $#total_bw+1;
#print "\nTotal bandwidth for $num_nodes nodes: $thetotal bytes/sec\n";
