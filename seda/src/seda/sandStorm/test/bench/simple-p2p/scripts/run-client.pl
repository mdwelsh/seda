#!/usr/bin/perl

$HOSTNAME = "localhost";
$MIN_RATE = "1";
$MAX_RATE = "1024";
$SEC_PER_RUN = 10;

$date = `date`; chop $date;
$local = `hostname`; chop $local;
print "# Running SimpleP2PClient at $date\n";
print "# Client is $local, server is $HOSTNAME\n";
print "# Rate\tThroughput\tAvg RT\tMax RT\tStddev RT\t90th pct RT\n";

for ($rate = $MIN_RATE; $rate <= $MAX_RATE; $rate *= 2) {
  $nummsgs = $rate * $SEC_PER_RUN;
  $CMD = "java SimpleP2PClient $HOSTNAME $rate $nummsgs 2>&1";
#print "# CMD: $CMD\n";
  open(CMD, "$CMD|") || die "Can't run $CMD\n";
  open(TMPFILE, ">/tmp/mdw.run.$$") || die "Can't open tmpfile\n";

  while (<CMD>) {
#print "# READ: $_";

    if (/^Overall rate:\s+(\S+) msgs/) {
      $throughput = $1;
    }

    if (/^RT (\S+) ms (\S+) count/) {
      print TMPFILE;
    }
  }
  close(TMPFILE);
  close(CMD);

  $SCMD = "cat /tmp/mdw.run.$$ | stats.pl -c 4 2";
  open (SCMD, "$SCMD|") || die "Can't run $SCMD\n";
  while (<SCMD>) {
    if (/^mean: (\S+)/) {
      $avg_rt = $1;
    }
    if (/^max: (\S+)/) {
      $max_rt = $1;
    }
    if (/^stddev: (\S+)/) {
      $stddev_rt = $1;
    }
    if (/^90th percentile: (\S+)/) {
      $nth_rt = $1;
    }
  }
  close(SCMD);
  `rm -f /tmp/mdw.run.$$`;

  print "$rate\t$throughput\t$avg_rt\t$max_rt\t$stddev_rt\t$nth_rt\n";

}

