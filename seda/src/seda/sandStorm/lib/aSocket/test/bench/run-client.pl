#!/usr/bin/perl

# Front-end script to run MultiClient save logs

if (($#ARGV != 3) && ($#ARGV != 4)) {
  print "Usage: run-client.pl [-p] <logdir> <server> <numnodes> <numclienttheads>\n";
  print "Options:\n";
  print "\t-p\tDisplay output directly; do not send to file\n";
  exit -1;
}

$LOGDIR = shift;
if ($LOGDIR eq "-p") {
  $DIRECT_OUTPUT = 1;
  $LOGDIR = shift;
}
$SERVER = shift;
$NUMNODES = shift;
$NUMCLIENTTHREADS = shift;

$SENDMSGSIZE = 8192;
$SENDBURSTSIZE = 1000;
$RECVMSGSIZE = 32;
$RECVBURSTSIZE = 1;

if (!$DIRECT_OUTPUT) {
  if (system("mkdir -p $LOGDIR")) { die "Can't run mkdir\n"; }
}

$logfile = "$LOGDIR/LOG.$NUMNODES.$NUMCLIENTTHREADS.$SENDMSGSIZE.$SENDBURSTSIZE.$RECVMSGSIZE.$RECVBURSTSIZE";

if (!$DIRECT_OUTPUT) {
  $out = "> $logfile 2>&1";
} else {
  "2>&1";
}

$cmd = "rexec -n $NUMNODES java -ms256M -mx256M MultiClient $SERVER $NUMCLIENTTHREADS $SENDMSGSIZE $SENDBURSTSIZE $RECVMSGSIZE $RECVBURSTSIZE $out";
#$cmd = "safe-rexec -n $NUMNODES run-client-localdisk.sh $SERVER $NUMCLIENTTHREADS $SENDMSGSIZE $SENDBURSTSIZE $RECVMSGSIZE $RECVBURSTSIZE $out";

print STDERR "Running for $logfile\n";

system($cmd);
