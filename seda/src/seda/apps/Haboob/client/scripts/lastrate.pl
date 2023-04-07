#!/usr/bin/perl

# lastrate.pl - prints out last rate sample from a log file

while (<>) {
  if (/(\d+)\s+Overall rate:\s+(\S+)/) {
    $rate[$1] = $2;
    $num_rate_samples[$1]++;
  }
  if (/(\d+)\s+Bandwidth:\s+(\S+)/) {
    $bw[$1] = $2;
    $num_bw_samples[$1]++;
  }
}

for ($i = 0; $i <= $#rate; $i++) {
  if ($num_rate_samples[$i] != 0) {
    $num_nodes++;
    print "Node $i had $num_rate_samples[$i] samples, last $rate[$i] comps/sec\n";
    $total_rate += $rate[$i];
  }
}

print "\nTotal rate for $num_nodes nodes: $total_rate comps/sec\n";

for ($i = 0; $i <= $#bw; $i++) {
  if ($num_bw_samples[$i] != 0) {
    print "Node $i had $num_bw_samples[$i] samples, last $bw[$i] bytes/sec\n";
    $total_bw += $bw[$i];
  }
}

$total_bw_mbps = ($total_bw * 8) / (1024.0 * 1024.0);
print "\nTotal bandwidth for $num_nodes nodes: $total_bw bytes/sec ($total_bw_mbps Mbit/sec)\n";
