#!/usr/bin/perl

# Skip this many samples at the beginning of a run
$SKIP_SAMPLES = 3;

# Maximum number of samples to consider
$MAX_SAMPLES = 100;

while (<>) {
  if (/(\d+)\s+Overall rate:\s+(\S+)/) {
    if ($num_rate_samples[$1] < $MAX_SAMPLES) {
      if ($num_rate_samples[$1] > $SKIP_SAMPLES) {
        $total_rate[$1] += $2;
      }
      $num_rate_samples[$1]++;
    }
  }
  if (/(\d+)\s+Bandwidth:\s+(\S+)/) {
    if ($num_bw_samples[$1] < $MAX_SAMPLES) {
      if ($num_bw_samples[$1] > $SKIP_SAMPLES) {
        $total_bw[$1] += $2;
      }
      $num_bw_samples[$1]++;
    }
  }
}

for ($i = 0; $i <= $#total_rate; $i++) {
  if (($num_rate_samples[$i] - $SKIP_SAMPLES) > 0) {
    $num = $num_rate_samples[$i] - $SKIP_SAMPLES;
    $avg_rate[$i] = $total_rate[$i] / $num;
    print "Node $i had $num_rate_samples[$i] samples, avg $avg_rate[$i] comps/sec\n";
    $thetotal += $avg_rate[$i];

  } else {
    print "Node $i had 0 samples\n";
  }
}

$num_nodes = $#total_rate+1;
print "\nTotal rate for $num_nodes nodes: $thetotal comps/sec\n\n";

for ($i = 0; $i <= $#total_bw; $i++) {
  if (($num_bw_samples[$i] - $SKIP_SAMPLES) > 0) {
    $num = $num_bw_samples[$i] - $SKIP_SAMPLES;
    $avg_bw[$i] = $total_bw[$i] / $num;
    print "Node $i had $num_bw_samples[$i] samples, avg $avg_bw[$i] bytes/sec\n";
    $thetotal += $avg_bw[$i];

  } else {
    print "Node $i had 0 samples\n";
  }
}

$num_nodes = $#total_bw+1;
$mbps = ($thetotal * 8.0) / (1024.0 * 1024.0);
print "\nTotal bandwidth for $num_nodes nodes: $thetotal bytes/sec, $mbps Mbps\n";
