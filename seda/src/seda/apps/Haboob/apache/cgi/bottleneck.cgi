#!/usr/bin/perl

$| = 1;

$NUM_RUNS = 10;
$NUM_SUMS = 10;
$NUM_BYTES_TO_READ = 8000;
$OUTPUT_LENGTH = 8192;
$RANDOMFILE = "random.data";

print "Content-type: text/plain\n";
print "Content-Length: $OUTPUT_LENGTH\n\n";

for ($run = 0; $run < $NUM_RUNS; $run++) {

  open (RANDFILE, $RANDOMFILE) || die "Cannot open $RANDOMFILE\n";
  for ($i = 0; $i < $NUM_BYTES_TO_READ; $i++) {
    sysread(RANDFILE, $data[$i], 1);
  }
  close(RANDFILE);

  for ($n = 0; $n < $NUM_SUMS; $n++) {
    $sum = 0;
    for ($i = 0; $i < $NUM_BYTES_TO_READ; $i++) {
      $sum += $data[$i];
    }
  }
}

for ($i = 0; $i < $OUTPUT_LENGTH; $i++) {
  print "x";
}

#print "Sum of $NUM_BYTES_TO_READ bytes of random data, $NUM_SUMS times: $sum\n";
#($user, $system, $cuser, $csystem) = times;
#print "User time: $user\n";
#print "System time: $system\n";
#print "Children user time: $cuser\n";
#print "Children system time: $csystem\n";
