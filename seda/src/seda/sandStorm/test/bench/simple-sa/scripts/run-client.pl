#!/usr/bin/perl

$MIN_RATE = 64;
$MAX_RATE = 1024;
$CFG = "exp-cpu-rtcon.cfg";
$TARGET_RT = 200.0;

$SEC_PER_RUN = 100;

$date = `date`; chop $date;
$local = `hostname`; chop $local;
print "# Running Simple-SA benchmark at $date\n";
print "# Running on $local\n";
print "# Rate\tThroughput\tAvg RT\tMax RT\t90th pct RT\tFrac Rej\n";

for ($rate = $MIN_RATE; $rate <= $MAX_RATE; $rate *= 2) {
  $nummsgs = $rate * $SEC_PER_RUN;
  $CMD = "sandstorm $CFG rate=$rate num_msgs=$nummsgs global.rtController.targetResponseTime=$TARGET_RT 2>&1";
#print "# CMD: $CMD\n";
  open(CMD, "$CMD|") || die "Can't run $CMD\n";
  open(TMPFILE, ">/tmp/mdw.run.$$") || die "Can't open tmpfile\n";
  open(TMPFILEREJ, ">/tmp/mdw.rej.$$") || die "Can't open tmpfilerej\n";

  while (<CMD>) {
#print "# READ: $_";

    if (/^Overall rate:\s+(\S+) msgs/) {
      $throughput = $1;
    }

    if (/^(\d+) rejected, fraction (\S+)/) {
      $rejfrac = $2;
    }

    if (/^RT (\S+) ms (\S+) count/) {
      print TMPFILE;
    }

    if (/^REJRT (\S+) ms (\S+) count/) {
      print TMPFILEREJ;
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

  `rm -f /tmp/mdw.rej.$$`;

  print "$rate\t$throughput\t$avg_rt\t$max_rt\t$nth_rt\t$rejfrac\n";

}

