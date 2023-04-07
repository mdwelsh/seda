#!/usr/bin/perl

# avgrest-hist.pl: Determine average, max, and standard deviation of 
# connect, response, and combined response (connect+response) times
# for a benchmark run. Makes use of 'histres.pl' to extract measurements
# from log files, and 'stats.pl' to calculate statistics.
#
# Matt Welsh, mdw@cs.berkeley.edu

$FNAME = $ARGV[0];
if (!$FNAME) {
  print STDERR "Usage: avgres-hist.pl <logfile>\n";
  exit -1;
}

# conntime
$scmd = "histres.pl $FNAME | grep '^conntime' | stats.pl -c 4 2";
open (SCMD, "$scmd|") || die "Can't run $scmd\n";
while (<SCMD>) {
  if (/^mean: (\S+)/) {
    $avg_conn = $1;
  }
  if (/^max: (\S+)/) {
    $max_conn = $1;
  }
  if (/^stddev: (\S+)/) {
    $stddev_conn = $1;
  }
  if (/^90th percentile: (\S+)/) {
    $nth_conn = $1;
  }
}
close(SCMD);

# resptime
$scmd = "histres.pl $FNAME | grep '^resptime' | stats.pl -c 4 2";
open (SCMD, "$scmd|") || die "Can't run $scmd\n";
while (<SCMD>) {
  if (/^mean: (\S+)/) {
    $avg_resp = $1;
  }
  if (/^max: (\S+)/) {
    $max_resp = $1;
  }
  if (/^stddev: (\S+)/) {
    $stddev_resp = $1;
  }
  if (/^90th percentile: (\S+)/) {
    $nth_resp = $1;
  }
}
close(SCMD);

# combresptime
$scmd = "histres.pl $FNAME | grep '^combresptime' | stats.pl -c 4 2";
open (SCMD, "$scmd|") || die "Can't run $scmd\n";
while (<SCMD>) {
  if (/^mean: (\S+)/) {
    $avg_cresp = $1;
  }
  if (/^max: (\S+)/) {
    $max_cresp = $1;
  }
  if (/^stddev: (\S+)/) {
    $stddev_cresp = $1;
  }
  if (/^90th percentile: (\S+)/) {
    $nth_cresp = $1;
  }
}
close(SCMD);

print "\nAverage connect time: $avg_conn ms, max $max_conn ms 90th $nth_conn ms stddev $stddev_conn\n";
print "Average response time: $avg_resp ms, max $max_resp ms 90th $nth_resp ms stddev $stddev_resp\n";
print "Average combined response time: $avg_cresp ms, max $max_cresp ms 90th $nth_cresp ms stddev $stddev_cresp\n";
