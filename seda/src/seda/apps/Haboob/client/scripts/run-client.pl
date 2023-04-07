#!/usr/bin/perl

# run-client.pl: Front-end script to run load generator and save logs
# Can be used directly or via 'do-run.pl'
#
# Matt Welsh, mdw@cs.berkeley.edu

if (($#ARGV != 2) && ($#ARGV != 3)) {
  print "Usage: run-client.pl [-p] <logdir> <numnodes> <numclienttheads>\n";
  print "Options:\n";
  print "\t-p\tDisplay output directly; do not send to file\n";
  exit -1;
}

$LOGDIR = shift;
if ($LOGDIR eq "-p") {
  $DIRECT_OUTPUT = 1;
  $LOGDIR = shift;
}
$NUMNODES = shift;
$NUMCLIENTTHREADS = shift;

# Set to true to run on a single node (i.e. fake-gexec)
$FAKE_GEXEC = 0;

if ($FAKE_GEXEC) {
  $NUMCLIENTTHREADS = $NUMNODES * $NUMCLIENTTHREADS;
  $NUMNODES = 1;
}

# Determine number of runs -- need more for more clients to get confidence
# Also, use threaded client for small number of clients - better throughput
$TOTAL_CLIENTS = $NUMNODES * $NUMCLIENTTHREADS;
if ($TOTAL_CLIENTS <= 64) { 
  $JAVACLASS = "HttpLoad";
  $NUMBER_RUNS = 40;
} else {
  $JAVACLASS = "HttpLoad";
  $NUMBER_RUNS = 40;
}

# Set to true if Haboob stats should be retrieved before and after run
$GET_HABOOB_STATS = 0;

$URL = "http://mm56:8080";
$REQUESTDELAY = 20;
$LOADCONNECTIONS = 1000;

if ($LOADCONNECTIONS == 0) {
  $LOADCONNECTIONS = $NUMNODES * $NUMCLIENTTHREADS;
}

if (!$DIRECT_OUTPUT) {
  if (system("mkdir -p $LOGDIR")) { die "Can't run mkdir\n"; }
}

$logfile = "$LOGDIR/LOG.$NUMNODES.$NUMCLIENTTHREADS.$REQUESTDELAY.$LOADCONNECTIONS";

`rm -f $logfile`;

if (!$DIRECT_OUTPUT) {
  $out = ">> $logfile 2>&1";
} else {
  "2>&1";
}

if ($GET_HABOOB_STATS) {
  `echo \"# Initial Haboob stats #####\" $out`;
  `gethttp.pl $URL/HABOOB/ >> $logfile 2>&1`;
  `echo \"# Initial Haboob stats #####\" $out`;
}

if ($FAKE_GEXEC) {
  $cmd = "fake-gexec java -ms128M -mx512M -Djava.compiler=jitc $JAVACLASS $URL $NUMCLIENTTHREADS $REQUESTDELAY $LOADCONNECTIONS $NUMBER_RUNS $out";
} else {
  $cmd = "safe-gexec -n $NUMNODES java -ms128M -mx512M -Djava.compiler=jitc $JAVACLASS $URL $NUMCLIENTTHREADS $REQUESTDELAY $LOADCONNECTIONS $NUMBER_RUNS $out";
}

print STDERR "Running for $logfile\n";

system($cmd);
if ($?) {
  print STDERR "run-client command exited with return value $?\n";
  exit 1;
}

if ($GET_HABOOB_STATS) {
  `echo \"# Ending Haboob stats #####\" >> $logfile`;
  `gethttp.pl $URL/HABOOB/ >> $logfile 2>&1`;
  `echo \"# Ending Haboob stats #####\" >> $logfile`;
}
