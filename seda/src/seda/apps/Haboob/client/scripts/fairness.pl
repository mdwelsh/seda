#!/usr/bin/perl

# fairness.pl - measure fairness across clients for a benchmark run.
# Input is an gexec log with lines of the form
#   N Client C S sent, R received
# where N is the node number, C is the client number on that node,
# S is the number of requests sent, and R is the number of requests
# received. Calculates average fairness and standard error across
# all clients.
#
# Matt Welsh, mdw@cs.berkeley.edu

while (<>) {
  if (/^(\d+) Client (\d+) (\d+) sent, (\d+) received/) {
    $key = "$1:$2";
    $sent{$key} = $3;
    $received{$key} = $4;
  }

  # Get error rate
  if (/^(\d+) Requests sent: (\d+) total,/) {
    $totalreqs += $2;
  }

  # For HttpLoadThreaded
  if (/^(\d+) Errors: (\d+) total,/) {
    $totalerrors += $2;
  }

  # For HttpLoad
  if (/^(\d+) Rejections: (\d+) total,/) {
    $totalerrors += $2;
  }
}

foreach $key (sort keys(%sent)) {
  ($node, $client) = split(':', $key);
  $totalsent += $sent{$key};
  $totalrecv += $received{$key};
  $sqtotalsent += ($sent{$key} * $sent{$key});
  $sqtotalrecv += ($received{$key} * $received{$key});
  $count++;
}

if ($count == 0) {
  print "Total bursts sent 0, average 0, stddev 0 (err 0%, avg 0%)\n";
  print "Total bursts received 0, average 0, stddev 0 (err 0%, avg 0%)\n";
  exit 0;
}

if ($totalreqs == 0) {
  $errorrate = 0.0;
} else {
  $errorrate = $totalerrors / $totalreqs;
}

$avgsent = $totalsent / $count;
$avgrecv = $totalrecv / $count;

if ($avgsent == 0.0) { $avgsent = 0.000001; }
if ($avgrecv == 0.0) { $avgrecv = 0.000001; }

if (($count * $sqtotalsent) != 0) {
  $jain_sent = ($totalsent * $totalsent) / ($count * $sqtotalsent);
} else {
  $jain_sent = 0.0;
}

if (($count * $sqtotalrecv) != 0) {
  $jain_recv = ($totalrecv * $totalrecv) / ($count * $sqtotalrecv);
} else {
  $jain_recv = 0.0;
}

foreach $key (sort keys(%sent)) {
  ($node, $client) = split(':', $key);
  $ts += (($sent{$key} - $avgsent) * ($sent{$key} - $avgsent));
  $tr += (($received{$key} - $avgrecv) * ($received{$key} - $avgrecv));
}

if ($count > 1) {
  $stddev_sent = sqrt($ts / ($count - 1));
  $stddev_recv = sqrt($tr / ($count - 1));
  $err_sent = ($stddev_sent / $avgsent) * 100.0;
  $err_recv = ($stddev_recv / $avgrecv) * 100.0;
}

foreach $key (sort keys(%sent)) {
  ($node, $client) = split(':', $key);

  $ds = abs((($sent{$key} - $avgsent) / $avgsent) * 100.0);
  $dr = abs((($received{$key} - $avgrecv) / $avgrecv) * 100.0);
  $totalds += $ds; $totaldr += $dr;

  printf "Node %d client %d: sent %d (%.2f%%) recv %d (%.2f%%)\n",
    $node, $client, $sent{$key}, $ds, $received{$key}, $dr;
}

$avgds = $totalds / $count;
$avgdr = $totaldr / $count;

printf "Total bursts sent %d, average %.4f, stddev %.4f (err %.2f%%, avg %.2f%%) jain_fairness %.4f\n",
$totalsent, $avgsent, $stddev_sent, $err_sent, $avgds, $jain_sent;
printf "Total bursts received %d, average %.4f, stddev %.4f (err %.2f%%, avg %.2f%%) jain_fairness %.4f\n",
$totalrecv, $avgrecv, $stddev_recv, $err_recv, $avgdr, $jain_recv;
printf "Total errors %d, error rate %.4f\n", $totalerrors, $errorrate;
