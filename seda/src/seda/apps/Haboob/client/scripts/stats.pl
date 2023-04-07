#!/usr/bin/perl

# stats.pl by Matt Welsh <mdw@cs.berkeley.edu>
# 
# Reads in a histogram of <val>, <count> values and prints some
# basic statistics about it. Also generates histograms.

use Getopt::Std;

sub usage {
  print STDERR "Usage: stats.pl [options] <datacolumn>\n";
  print STDERR "  Prints statistics on data from the given file.\n";
  print STDERR "  <datacolumn> is the column of data values.\n";
  print STDERR "     (First column is 1)\n\n";
  print STDERR " Options:\n";
  print STDERR "\t-f <file>\t Read data from given file\n";
  print STDERR "\t-c <column>\t Specify column with counts\n";
  print STDERR "\t-k <key>\t Only consider lines containing regexp <key>\n";
  print STDERR "\t-h <bucketsize>\t Produce histogram of data\n";
  print STDERR "\t-g\t Graph histogram (requires -h)\n";
  print STDERR "\n";
  exit -1;
}

if ($#ARGV == -1) {
  &usage;
}

getopt('gf:c:k:h');
if ($opt_f) {
  $FNAME = $opt_f; 
}

if ($opt_c) {
  $COUNTCOLUMN = $opt_c;
} else {
  $COUNTCOLUMN = 0;
}

if ($opt_k) {
  $KEY = $opt_k;
}

if ($opt_h) {
  $BUCKETSIZE = $opt_h;
}

if ($opt_g) {
  $GRAPH = 1;
}

if ($#ARGV == -1) {
  &usage;
}

$DATACOLUMN = $ARGV[0];
if ($COUNTCOLUMN == 0) {
  print "Using data column $DATACOLUMN, no count column\n";
} else {
  print "Using data column $DATACOLUMN, count column $COUNTCOLUMN\n";
}
$DATACOLUMN--; $COUNTCOLUMN--;

if ($FNAME) {
  open(IN, "$FNAME") || die "Can't open $FNAME\n";
  $filehandle = \*IN;
} else {
  $filehandle = \*STDIN;
}

$first = 1;

while (<$filehandle>) {
  next if /^\#/; chop;

  if ($KEY) {
    next if !(/$KEY/);
  }
  if ($first) {
    print "First line of data:\n\n$_\n\n";
    $first = 0;
  }

  @vals = split;

  if ($COUNTCOLUMN == -1) {
    # 1 count per sample
    $data[$i++] = @vals[$DATACOLUMN];

    if ($BUCKETSIZE) {
      $bucket = @vals[$DATACOLUMN] / $BUCKETSIZE;
      $histogram[$bucket]++;
    }

  } else {
    $count = @vals[$COUNTCOLUMN];
    if ($count > $maxcount) {
      $maxcount = $count;
      $mode = @vals[$DATACOLUMN];
    }
    # Number of counts for each sample in countcolumn
    for ($j = 0; $j < $count; $j++) {
      $data[$i++] = @vals[$DATACOLUMN];
    }

    if ($BUCKETSIZE) {
      $bucket = @vals[$DATACOLUMN] / $BUCKETSIZE;
      $histogram[$bucket] += @vals[$COUNTCOLUMN];
    }

  }
}

close $filehandle;

# Sort data
@sorted = sort { $a <=> $b} @data;
$num = $#sorted + 1;

# Calculate median
if ($num % 2) {
  $median = ($sorted[$num/2] + $sorted[($num/2)+1]) / 2;
} else {
  $median = $sorted[$num/2];
}

# Calculate 10th and 90th percentile
$ten = int($num * 0.1);
$nine = int($num * 0.9);
$tenth = $sorted[$ten];
$ninetieth = $sorted[$nine];

# Calculate min and max
$min = $sorted[0];
$max = $sorted[$#sorted];

# Calculate mean
if ($num == 0) {
  $mean = 0;
} else {
  $total = 0;
  for ($i = 0; $i <= $#sorted; $i++) {
    $total += $sorted[$i];
  }
  $mean = $total / $num;
}

# Calculate stddev
if ($num < 2) { 
  $stddev = 0; 
} else {
  $total = 0;
  for ($i = 0; $i <= $#sorted; $i++) {
    $total += ($sorted[$i] - $mean) * ($sorted[$i] - $mean)
  }
  $stddev = sqrt($total / ($num - 1));
}

print "$num data points\n";
print "min: $min\n";
print "max: $max\n";
print "10th percentile: $tenth\n";
print "90th percentile: $ninetieth\n";
print "mean: $mean\n";
print "stddev: $stddev\n";
print "median: $median\n";
if ($COUNTCOLUMN != -1) {
  print "mode: $mode with count $maxcount\n";
}

if ($BUCKETSIZE) {
  print "\nHistogram follows:\n\n";
  for ($i = 0; $i <= $#histogram; $i++) {
    if ($histogram[$i] != 0) {
      $bucket = $i * $BUCKETSIZE;
      print "BUCKET $bucket COUNT $histogram[$i]\n";
    }
  }

  if ($GRAPH) {
    open(OUT, ">/tmp/stats-hist.$$") || die "Can't open /tmp/stats-hist.$$\n";
    for ($i = 0; $i <= $#histogram; $i++) {
      if ($histogram[$i] != 0) {
        $bucket = $i * $BUCKETSIZE;
        print OUT "BUCKET $bucket COUNT $histogram[$i]\n";
      }
    }
    close OUT;
    open(OUT, ">/tmp/stats-hist-graph.$$") || die "Can't open /tmp/stats-hist-graph.$$\n";
    print OUT "set title 'Histogram of $FNAME column $DATACOLUMN count $COUNTCOLUMN'\n";
    print OUT "set xlabel 'Bucket number (bucketsize $BUCKETSIZE)'\n";
    print OUT "set ylabel 'Count'\n";
    print OUT "set grid noxtics ytics\n";
    print OUT "plot [][0:] \"/tmp/stats-hist.$$\" using 2:4 with i lw 5\n";
    print OUT "pause -1 'Press return to exit...'\n";
    close (OUT);

    `gnuplot /tmp/stats-hist-graph.$$`;
    `rm -f /tmp/stats-hist.$$ /tmp/stats-hist-graph.$$`;

  }
  
}

