#!/usr/bin/perl

#gethttp:  a program that gets a document via http.  Usage is:
#gethttp documentname [savefile]
#
#Currently only supports http URLs, although it shouldn't take much more
#to allow it to retrive things via ftp.
#
#Output is to standard output if no save file name is given.

# Set to 1 to strip off incoming HTTP headers
$STRIP_HEADERS = 1;
# Set to 1 to submit URL to server as "/dir/dir/file" rather than as URL
$STRIP_URL_PATH = 1;

$0 =~ s/.*\///g;
$name = $0;
die "Usage:  ", $name, " URL [filename]\n" unless ($ARGV = shift);

$url = $ARGV;

($http, $temp) = split(m^//^, $url, 2);
die $name, " currently only supports http format retrieval.\n"
    unless $http eq "http:";
$url = $ARGV;
($temp, $path) = split(m^/^, $temp, 2);
if ($STRIP_URL_PATH) {
  $url = "/" . $path;
}
$command = "GET " . $url . " HTTP/1.0";

($theirhost, $port) = split(/:/, $temp,2);

##
#
# open up the socket
#
##

#constants that might already be set, but just in case...

$AF_INET = 2;
$SOCK_STREAM = 1;

# Formating string explained in the camel book.  Whaddya mean you don't
# own it?  GO BUY IT!

$fmt = "S n a4 x8";


#Put the identifier for the tcp protocol into $proto

($name, $aliases, $proto) = getprotobyname ("tcp");

#If $port is a name, get the proper number, if it's empty, make it 80

if ($port) {
    ($name, $aliases, $port) = getservbyname($port, "tcp")
	unless $port =~ /^\d+$/;
} else {
    $port = 80;
}

# What's my host's name?

chop($myhost = `hostname`);

# Put its IP number into $myaddr

($name, $aliases, $type, $len, $myaddr) =
    gethostbyname($myhost);

# Put the remote host's IP number into $theiraddr

($name, $aliases, $type, $len, $theiraddr) =
    gethostbyname ($theirhost);

# Set the two needed socket addresses

$here = pack($fmt, $AF_INET, 0, $myaddr);
$there = pack($fmt, $AF_INET, $port, $theiraddr);

# Now grab a connection to the remote host

die "$!\n" unless socket(SOCKET, $AF_INET, $SOCK_STREAM, $proto);
die "$!\n" unless bind(SOCKET, $here);
die "$!\n" unless connect(SOCKET, $there);

# Set up proper buffering on the socket...

select(SOCKET); $| = 1; select(STDOUT); $| = 1;

# If there was a second argument, open that up, otherwise use STDOUT

if ($ARGV = shift) {
    die ("Couldn't open $ARGV\n", $!)
	unless open(STDOUT, sprintf(">%s", $ARGV));
}

$command = "$command\r\nUser-Agent: Secret/1.0\r\n\r\n";
print SOCKET $command;
#print SOCKET "User-Agent: Secret/1.0\n";
#print SOCKET "\n";

#undef $/;

if (!$STRIP_HEADERS) {
  print <SOCKET>;
} else {
  $body_found = 0;

  while (<SOCKET>) {
    print $_;
#    if ($body_found) {
#      print "$_";
#    } else {
#      if (($_ eq "\r\n") || ($_ eq "\n")) {
#        $body_found = 1;
#      }
#    }
  }

}



