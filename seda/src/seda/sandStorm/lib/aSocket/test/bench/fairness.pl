#!/usr/bin/perl

while (<>) {
  if (/^(\d+) Client (\d+) (\d+) sent, (\d+) received/) {
    $key = "$1:$2";
    $sent{$key} = $3;
    $received{$key} = $4;
  }
}

foreach $key (sort keys(%sent)) {
  ($node, $client) = split(':', $key);
  $totalsent += $sent{$key};
  $totalrecv += $received{$key};
  $count++;
}

$avgsent = $totalsent / $count;
$avgrecv = $totalrecv / $count;


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

printf "Total bursts sent %d, average %.4f, stddev %.4f (err %.2f%%, avg %.2f%%)\n",
  $totalsent, $avgsent, $stddev_sent, $err_sent, $avgds;
printf "Total bursts received %d, average %.4f, stddev %.4f (err %.2f%%, avg %.2f%%)\n",
  $totalrecv, $avgrecv, $stddev_recv, $err_recv, $avgdr;
