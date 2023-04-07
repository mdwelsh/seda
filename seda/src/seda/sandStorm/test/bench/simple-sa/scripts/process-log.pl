#!/usr/bin/perl

print "# meas-sample meas-cont actual est target_mu mu lambda lambdahat rejrate numthreads threshold\n";

$measured = 0;
$measured_continuous = 0;
$rejected = 0;
$actual = 0;
$est = 0;
$mu = 0;
$lambda = 0;
$rate = 0;
$threshold = 0;
$threads = 0;
$target_mu = 0;

$count = 0;

while (<>) {

  if (/threshold now (\S+)/) {
    $threshold = $1;
  }

  if (/^TP <ProcessStage>: Adding (\S+) threads to pool, size (\S+)/) {
    $threads = $2;
  }

  if (/^RT: avg (\S+) max (\S+) 90th (\S+)/) {
    $measured = $3;
  }

  if (/^CRT: avg (\S+) max (\S+) 90th (\S+)/) {
    $measured_continuous = $3;
  }

  if (/rejected, fraction (\S+)/) {
    $rejected = $1;
    $count++;
    if ($count >= 3) { 
      print "$measured $measured_continuous $actual $est $target_mu $mu $lambda $rate $rejected $threads $threshold\n";
    }
  }

  if (/ninetiethRT (\S+) target (\S+) threshold (\S+)/) {
    $actual = $1;
    $threshold = $3;
    print "$measured $measured_continuous $actual $est $target_mu $mu $lambda $rate $rejected $threads $threshold\n";
  }

  if (/lambda (\S+) 90th (\S+)/) {
    $lambda = $1; $actual = $2;
  }

  if (/ninetiethRT (\S+) est (\S+) mu (\S+) lambda (\S+)/) {
    $actual = $1;
    $est = $2;
    $mu = $3;
    $lambda = $4;
  }

  if (/ProcessStage: setting mu: (\S+)/) {
    $target_mu = $1;
  }

  if (/rate now (\S+)/) {
    $rate = $1;
    print "$measured $measured_continuous $actual $est $target_mu $mu $lambda $rate $rejected $threads $threshold\n";
    $count = 0;
  }


}
