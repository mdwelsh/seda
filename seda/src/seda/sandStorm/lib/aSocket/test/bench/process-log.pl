#!/usr/bin/perl

# Process an entire directory of log entries

if ($#ARGV != 0) {
  print STDERR "Usage: process-log.pl <logdir>\n";
  exit -1;
}

$LOGDIR = shift;

$cmd = "ls $LOGDIR/LOG.*";

open (CMD, "$cmd|") || die "Can't run $cmd\n";
while (<CMD>) {
  chop;
  $fname = $_; 

  if ($fname =~ /LOG\.(\d+)\.(\d+)\.(\d+)\.(\d+)\.(\d+)\.(\d+)/) {
    $numnodes = $1;
    $numclientthreads = $2;
    $sendmsg = $3;
    $sendburst = $4;
    $recvmsg = $5;
    $recvburst = $6;
  }
  
  # Get average completion rate
  $scmd = "avgrate.pl $fname";
  open (SCMD, "$scmd|") || die "Can't run $scmd\n";
  while (<SCMD>) {
    if (/^Total rate for (\d+) nodes: (\S+)/) {
      $totalrate = $2;
    }
    if (/^Total bandwidth for (\d+) nodes: (\S+)/) {
      $totalbw = ($2 * 8.0) / (1024.0 * 1024.0);
    }
  }
  close(SCMD);

  # Get connect and request time
  $scmd = "avgres.pl $fname";
  open (SCMD, "$scmd|") || die "Can't run $scmd\n";
  while (<SCMD>) {
    if (/^Average connect time: (\S+) ms, max (\S+)/) {
      $avgconn = $1; $maxconn = $2;
    }
    if (/^Average response time: (\S+) ms, max (\S+)/) {
      $avgresp = $1; $maxresp = $2;
    }
  }
  close(SCMD);

  # Get fairness 
  $scmd = "fairness.pl $fname";
  open (SCMD, "$scmd|") || die "Can't run $scmd\n";
  while (<SCMD>) {
    if (/sent.*err ([^%]+)%/) {
      $senderr = $1;
    }
    if (/received.*err ([^%]+)%/) {
      $recverr = $1;
    }
  }
  close(SCMD);

  $totalclients = $numnodes * $numclientthreads;

  $thedata = join(' ', $numnodes, $numclientthreads, $sendmsg, $sendburst, $recvmsg, $recvburst, $avgresp, $maxresp, $avgconn, $maxconn, $senderr, $recverr, $totalrate, $totalbw);
  $data[$totalclients] = $thedata;
}
close (CMD);

$date = `date`; chop $date;
print "# Data generated from $LOGDIR\n";
print "# process-log.pl ran on $date\n";
print "# nodes threads clients sendmsg sendburst recvmsg recvburst avgresp maxresp avgconn maxconn senderr recverr totalrate totalbw\n";
print "\n";

for ($totalclients = 1; $totalclients <= $#data; $totalclients++) {
  $thedata = $data[$totalclients];
  ($numnodes, $numclientthreads, $sendmsg, $sendburst, $recvmsg, $recvburst, $avgresp, $maxresp, $avgconn, $maxconn, $senderr, $recverr, $totalrate, $totalbw) = split(' ', $thedata);
  if ($numnodes != 0) {
    printf ("%d %d %d %d %d %d %d\t\t%.4f %.4f %.4f %.4f\t %.4f %.4f %.4f %.4f\n",
      $numnodes, $numclientthreads, $totalclients,
      $sendmsg, $sendburst, $recvmsg, $recvburst,
      $avgresp, $maxresp, $avgconn, $maxconn, 
      $senderr, $recverr,
      $totalrate, $totalbw);
  }
}
