#!/usr/bin/perl

package MDWBottleneck;
use Apache::Constants ':common';

$NUM_RUNS = 10;
$NUM_SUMS = 10;
$NUM_BYTES_TO_READ = 8000;
$OUTPUT_LENGTH = 8192;
$RANDOMFILE = "/scratch/mdw/specweb99-runs/cgi-bin/random.data";

sub handler {
 my $r = shift;
 $r->content_type('text/html');
 $r->no_cache(1);
 $r->send_http_header;

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
   $r->print("x");
 }
 OK;
}

1;
