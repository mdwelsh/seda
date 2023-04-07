#!/usr/bin/perl

# bwsplit.pl - split out bandwidth for each node

$FNAME = $ARGV[0];

open(INFILE, "$FNAME") || die "Can't open $FNAME\n";

while (<>) {
  if (/(\d+)\s+Bandwidth:\s+(\S+)/) {
    $node[$1] = 1;
  }
}
close(INFILE);

$numnodes = $#node - 1;
print STDERR "$numnodes nodes found.\n";
if ($numnodes == 0) { exit 0; }

open(BWGR,">BWGR") || die "Can't open BWGR\n";
print BWGR "plot \\\n";

for ($i = 0; $i < $numnodes; $i++) {
  print STDERR "Processing BW.$i\n";
  `grep "^$i Bandwidth:" $FNAME > BW.$i`;
  print BWGR "\"BW.$i\" u 3 w p";
  if ($i != $numnodes-1) {
    print BWGR ",\\\n";
  } else {
    print BWGR "\npause -1\n";
  }
}
close(BWGR);

