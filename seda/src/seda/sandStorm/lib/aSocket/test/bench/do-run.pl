#!/usr/bin/perl

# Do a run for a range of parameters

if (($#ARGV != 0) && ($#ARGV != 1)) {
  print STDERR "Usage: do-run.pl [-p] <logdir>\n";
  print STDERR "Options:\n";
  print STDERR "\t-p\tDisplay output directly; do not send to log file\n";
  exit -1;
}

$LOGDIR = shift;
if ($LOGDIR eq "-p") {
  $DIRECT_OUTPUT = 1;
  $LOGDIR = shift;
}

$SERVER = "mm56";

$MINCLIENTS = 256;
$MAXCLIENTS = 8192;

$MAXNODES = 16;

sub calcNodes {
  my ($numclients) = shift;

  $l = int(log($numclients) / log(2));
  if (($l % 2) != 0) {
    $nodes = 2 ** (($l-1)/2);
    $threads = 2 ** (($l+1)/2);
  } else {
    $nodes = 2 ** ($l/2);
    $threads = 2 ** ($l/2);
  }

  while ($nodes > $MAXNODES) {
    $nodes /= 2;
    $threads *= 2;
  }

  return ($nodes, $threads);
}

for ($totalclients = $MINCLIENTS; $totalclients <= $MAXCLIENTS; $totalclients *= 2) {
  ($nodes, $threads) = &calcNodes($totalclients);
  $t = $nodes * $threads;
  print STDERR "Target is $totalclients, nodes=$nodes, threads=$threads, total=$t\n";

  if ($DIRECT_OUTPUT) {
    $opt = "-p";
  }

  $cmd = "run-client.pl $opt $LOGDIR $SERVER $nodes $threads";
  print STDERR "Cmd is $cmd\n";
  system($cmd);
  print STDERR "Done, sleeping ...\n";
  sleep(10);
}

