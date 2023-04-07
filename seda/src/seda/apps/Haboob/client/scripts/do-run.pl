#!/usr/bin/perl

# do-run.pl: Do a benchmark run for a range of parameters
# This script drives 'run-client.pl' from $MINCLIENTS to $MAXCLIENTS
# total connections to the server. Output is placed in the given log
# directory.
#
# Matt Welsh, mdw@cs.berkeley.edu

$SIG{INT} = sub { die "do-run.pl killed by SIGINT\n"; };

if ($#ARGV > 0) {
  print STDERR "Usage: do-run.pl [-p] [logdir]\n";
  print STDERR "Options:\n";
  print STDERR "\t-p\tDisplay output directly; do not send to log file\n";
  exit -1;
}

$LOGDIR = shift;
if ($LOGDIR eq "-p") {
  $DIRECT_OUTPUT = 1;
}

# Minimum number of clients 
$MINCLIENTS = 1;
# Maximum number of clients
$MAXCLIENTS = 1024;

# Do run in reverse order?
$REVERSE = 0;

# Max number of nodes to run on
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

sub runit {
  my ($totalclients) = shift;

  ($nodes, $threads) = &calcNodes($totalclients);
  $t = $nodes * $threads;
  print STDERR "Target is $totalclients, nodes=$nodes, threads=$threads, total=$t\n";

  if ($DIRECT_OUTPUT) {
    $opt = "-p";
    $LOGDIR = "fake";
  }

  $cmd = "run-client.pl $opt $LOGDIR $nodes $threads";
  print STDERR "Cmd is $cmd\n";
  system($cmd);
  if ($?) {
    print STDERR "do-run.pl command exited with return value $?\n";
    exit 1;
  }
}

if ($REVERSE) {
  for ($totalclients = $MAXCLIENTS; $totalclients >= $MINCLIENTS; $totalclients /= 2) {
  &runit($totalclients);
  }
} else {
  for ($totalclients = $MINCLIENTS; $totalclients <= $MAXCLIENTS; $totalclients *= 2) {
  &runit($totalclients);
  }
}

