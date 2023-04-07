#!/usr/bin/perl

$MIN_LAMBDA = 256;
$MAX_LAMBDA = 256;

$MIN_MU = 1024; 
$MAX_MU = 1024;

$NUM_TARGETS = 5;

$CFG = "exp-cpu-tp.cfg";

$SEC_PER_RUN = 200;

$date = `date`; chop $date;
$local = `hostname`; chop $local;
print "# Running Simple-SA benchmark at $date\n";
print "# Running on $local\n";
print "# lambda\tmu\ttarget\ttput\tavgrt\tmaxrt\t90th-pc\trejected\n";

for ($lambda = $MIN_LAMBDA; $lambda <= $MAX_LAMBDA; $lambda *= 2) {
  for ($mu = $MIN_MU; $mu <= $MAX_MU; $mu *= 2) {
    $target = ((2.3 / $mu) * 1.0e3);
    for ($n = 0; $n < $NUM_TARGETS; $n++) {
      $nummsgs = $SEC_PER_RUN * $lambda;
      $CMD = "sandstorm $CFG rate=$lambda mu=$mu num_msgs=$nummsgs global.rtController.targetResponseTime=$target 2>&1";
print "# CMD IS $CMD\n";

      open(CMD, "$CMD|") || die "Can't run $CMD\n";

      while (<CMD>) {
	#print "# READ: $_";

	if (/^Overall rate:\s+(\S+) msgs/) {
	  $throughput = $1;
	}

	if (/^RT: avg (\S+) max (\S+) 90th (\S+)/) {
	  $avg_rt = $1;
	  $max_rt = $2;
	  $nth_rt = $3;
	}

	if (/^(\d+) rejected, fraction (\S+)/) {
	  $rejfrac = $2;
	}
      }
      close(CMD);

      printf("%.4g\t%.4g\t%.4g\t%.4g\t%.4g\t%.4g\t%.4g\t%.4g\n", 
      $lambda, $mu, $target, $throughput, $avg_rt, $max_rt, $nth_rt,
      $rejfrac);

      $target *= 2;
    }
  }
}


