#!/usr/bin/perl

# avgrate.pl - determine total completion rate and bandwidth for a
# benchmark run. Input is an gexec log file with lines of the form:
#   N Overall rate: R 
#   N Bandwidth: B
# where 'N' is the node number, 'R' is a completion rate (in completions
# per second), and 'B' is a bandwidth (in bytes per sec).
#
# Matt Welsh, mdw@cs.berkeley.edu

# Skip this many samples at the beginning of a run
$SKIP_SAMPLES = 20;

# Maximum number of samples to consider
$MAX_SAMPLES = 30;

while (<>) {
  if (/(\d+)\s+Overall rate:\s+(\S+)/) {
    if ($num_rate_samples[$1] < $MAX_SAMPLES) {
      if ($num_rate_samples[$1] >= $SKIP_SAMPLES) {
        $total_rate[$1] += $2;
      }
      $num_rate_samples[$1]++;
    }
  }
  if (/(\d+)\s+Bandwidth:\s+(\S+)/) {
    if ($num_bw_samples[$1] < $MAX_SAMPLES) {
      if ($num_bw_samples[$1] >= $SKIP_SAMPLES) {
        $total_bw[$1] += $2;
      }
      $num_bw_samples[$1]++;
    }
  }
}

$thetotal = 0;
for ($i = 0; $i <= $#total_rate; $i++) {
  if (($num_rate_samples[$i] - $SKIP_SAMPLES) > 0) {
    $num = $num_rate_samples[$i] - $SKIP_SAMPLES;
    $avg_rate[$i] = $total_rate[$i] / $num;
    print "Node $i had $num samples, avg $avg_rate[$i] comps/sec\n";
    $thetotal += $avg_rate[$i];

  } else {
    print "Node $i had 0 samples\n";
  }
}

$num_nodes = $#total_rate+1;
print "\nTotal rate for $num_nodes nodes: $thetotal comps/sec\n\n";

$thetotal = 0;
for ($i = 0; $i <= $#total_bw; $i++) {
  if (($num_bw_samples[$i] - $SKIP_SAMPLES) > 0) {
    $num = $num_bw_samples[$i] - $SKIP_SAMPLES;
    $avg_bw[$i] = $total_bw[$i] / $num;
    print "Node $i had $num samples, avg $avg_bw[$i] bytes/sec\n";
    $thetotal += $avg_bw[$i];

  } else {
    print "Node $i had 0 samples\n";
  }
}

$num_nodes = $#total_bw+1;
$mbps = ($thetotal * 8.0) / (1024.0 * 1024.0);
print "\nTotal bandwidth for $num_nodes nodes: $thetotal bytes/sec ($mbps Mbit/sec)\n";
