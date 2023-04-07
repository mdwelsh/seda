#!/usr/bin/perl

# process-log.pl Process an entire directory of log entries
# Uses the various scripts to generate a single data file summarizing
# a benchmark run.
#
# Matt Welsh, mdw@cs.berkeley.edu

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

  if ($fname =~ /(.*LOG.*)\.gz$/) {
    $GZIP = 1;
    `gunzip $fname`;
    $fname = $1;
  } else {
    $GZIP = 0;
  }

  # Determine basic run parameters
  if ($fname =~ /LOG\.(\d+)\.(\d+)\.(\d+)/) {
    $numnodes = $1;
    $numclientthreads = $2;
    $requestdelay = $3;
    $totalclients = $numnodes * $numclientthreads;
    print STDERR "$totalclients... ";
  }

  # Get average completion rate
  $scmd = "avgrate.pl $fname";
  $totalrate = $totalbw = 0.0;
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
  $scmd = "avgres-hist.pl $fname";
  $avgconn = $maxconn = $stddevconn = $avgresp = $maxresp = $stddevresp = $avgcresp = $maxcresp = $stddevcresp = $nthcresp = $nthresp = $nthconn = 0.0;
  open (SCMD, "$scmd|") || die "Can't run $scmd\n";
  while (<SCMD>) {
    if (/^Average connect time: (\S+) ms, max (\S+) ms 90th (\S+) ms stddev (\S+)/) {
      $avgconn = $1; $maxconn = $2; $nthconn = $3; $stddevconn = $4;
    }
    if (/^Average response time: (\S+) ms, max (\S+) ms 90th (\S+) ms stddev (\S+)/) {
      $avgresp = $1; $maxresp = $2; $nthresp = $3; $stddevresp = $4;
    }
    if (/^Average combined response time: (\S+) ms, max (\S+) ms 90th (\S+) ms stddev (\S+)/) {
      $avgcresp = $1; $maxcresp = $2; $nthcresp = $3; $stddevcresp = $4;
    }
  }
  close(SCMD);

  # Get fairness 
  $scmd = "fairness.pl $fname";
  $send_fair = $recv_fair = $reject_rate = 0.0;
  open (SCMD, "$scmd|") || die "Can't run $scmd\n";
  while (<SCMD>) {
    if (/sent.*jain_fairness (\S+)/) {
      $send_fair = $1;
    }
    if (/received.*jain_fairness (\S+)/) {
      $recv_fair = $1;
    }
    if (/reject rate (\S+)/) {
      $reject_rate = $1;
    }
  }
  close(SCMD);

  # Get Haboob statistics
  $hits = $misses = 0;
  $pcthits = $pctmisses = 0;
  $scmd = "haboobstats.pl $fname";
  open (SCMD, "$scmd|") || die "Can't run $scmd\n";
  while (<SCMD>) {
    if (/^Cache Hits\s+(\d+)\s+(\d+)\s+(\d+)/) {
      $hits = $3;
    }
    if (/^Cache Misses\s+(\d+)\s+(\d+)\s+(\d+)/) {
      $misses = $3;
    }
  }
  $totalreqs = $hits + $misses;
  if ($totalreqs != 0) {
    $pcthits = ($hits/$totalreqs) * 100.0;
    $pctmisses = ($misses/$totalreqs) * 100.0;
  }

  close(SCMD);

  if ($GZIP) {
    `gzip -9 $fname`;
  }


  $thedata = join(' ', $numnodes, $numclientthreads, $requestdelay, $avgcresp, $maxcresp, $nthcresp, $stddevcresp, $avgresp, $maxresp, $nthresp, $stddevresp, $avgconn, $maxconn, $nthconn, $stddevconn, $send_fair, $recv_fair, $pcthits, $pctmisses, $totalrate, $totalbw, $reject_rate);
  $data[$totalclients] = $thedata;
}
close (CMD);

print STDERR "\n";

$date = `date`; chop $date;
print "# Data generated from $LOGDIR\n";
print "# process-log.pl ran on $date\n";
print "#\n";
print "# Each line represents one run of the server.\n";
print "# The field descriptions are:\n";
print "#   1  nodes         Number of nodes in the run\n";
print "#   2  threads       Number of threads per node\n";
print "#   3  tclients      Total number of clients (nodes * threads)\n";
print "#   4  reqdelay      Client think time in ms\n";
print "#\n";
print "#   5  avgcresp      Average combined request time in ms\n";
print "#   6  maxcresp      Max combined request time in ms\n";
print "#   7  nthcresp      90th percentile of combined request time in ms\n";
print "#   8  stddevcresp   Standard deviation of combined request time\n";
print "#   9  avgresp       Average request time in ms (not counting connection delay)\n";
print "#   10 maxresp       Max request time in ms (not counting conn)\n";
print "#   11 nthresp       90th percentile of request time in ms\n";
print "#   12 stddevresp    Standard deviation of request time (not counting conn)\n";
print "#   13 avgconn       Average connect time in ms\n";
print "#   14 maxconn       Max connect time in ms\n";
print "#   15 nthconn       90th percentile of connect time in ms\n";
print "#   16 stddevconn    Standard deviation of connect time\n";
print "#   17 send_fair     Jain Fairness of send rate across clients\n";
print "#   18 recv_fair     Jain Fairness of receive rate across clients\n";
print "#   19 pcthits       Percent hits to page cache (Haboob only)\n";
print "#   20 pctmisses     Percent misses to page cache (Haboob only)\n";
print "#   21 totalrate     Total rate in requests/sec\n";
print "#   22 totalbw       Total bandwidth in Mbits/sec\n";
print "#   23 reject_rate   Frequency of rejected requests\n";
print "#\n";

print "# nodes threads clients reqdelay avgcresp maxcresp nthcresp stddevcresp avgresp maxresp nthresp stddevresp avgconn maxconn nthconn stddevconn send_fair recv_fair cachehits cachemisses totalrate totalbw reject_rate\n";
print "\n";

for ($totalclients = 1; $totalclients <= $#data; $totalclients++) {
  $thedata = $data[$totalclients];
  ($numnodes, $numclientthreads, $requestdelay, $avgcresp, $maxcresp, $nthcresp, $stddevcresp, $avgresp, $maxresp, $nthresp, $stddevresp, $avgconn, $maxconn, $nthconn, $stddevconn, $send_fair, $recv_fair, $pcthits, $pctmisses, $totalrate, $totalbw, $reject_rate) = split(' ', $thedata);
  if ($numnodes != 0) {
    printf ("%d %d %d %d   %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f\n", 
	$numnodes, $numclientthreads, $totalclients, $requestdelay, 
	$avgcresp, $maxcresp, $nthcresp, $stddevcresp, 
	$avgresp, $maxresp, $nthresp, $stddevresp, 
	$avgconn, $maxconn, $nthconn, $stddevconn, 
	$send_fair, $recv_fair, $pcthits, $pctmisses,
	$totalrate, $totalbw, $reject_rate);
  }
}
