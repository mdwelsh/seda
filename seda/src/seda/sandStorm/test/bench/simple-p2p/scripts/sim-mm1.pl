#!/usr/bin/perl

$MIN_RATE = "1";
$MAX_RATE = "1024";
$MU = 2000.0;

$date = `date`; chop $date;
$local = `hostname`; chop $local;
print "# Simulated client assuming M/M/1\n";
print "# MU is $MU\n";
print "# Rate\tThroughput\tAvg RT\tMax RT\tStddev RT\t90th pct RT\n";

for ($rate = $MIN_RATE; $rate <= $MAX_RATE; $rate *= 2) {
  $rho = ($rate * 1.0) / ($MU * 1.0);
  $throughput = $rate;
  $avg_rt = ((1.0 / $MU) / (1.0 - $rho)) * 1.0e3;
  $max_rt = -1.0;
  $stddev_rt = sqrt((1.0 / ($MU * $MU)) / ((1.0 - $rho) * (1.0 - $rho))) * 1.0e3;
  $nth_rt = 2.3 * $avg_rt;

  printf("%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\n", $rate, $throughput, 
      $avg_rt, $max_rt, $stddev_rt, $nth_rt);

}

