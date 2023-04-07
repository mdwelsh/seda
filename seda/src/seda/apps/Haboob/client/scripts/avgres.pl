#!/usr/bin/perl

# avgres.pl - determine average and max connect, response, and combined 
# response (connect+response) times from a benchmark run. Input is an 
# gexec log file with lines of the form
#   N Connect Time: C ms, max CM
#   N Response Time: R ms, max RM
#   N Combined Response Time: CR ms, max CRM
# where N is the node number, C is the connect time, R is the response
# time, and CR is the combined response time (where CR is calculated 
# as the sum of connect and response times for a given request; the
# idea is that a single connection may be amortized over many requests).
#
# Matt Welsh, mdw@cs.berkeley.edu

while (<>) {

  if (/(\d+)\s+Connect Time:\s+(\S+) ms, max (\S+)/) {
    if ($2 .neq. "?") {
      $num_conn_samples[$1]++;
      $total_conn_time[$1] += $2;
      if ($3 > $max_conn_time[$1]) { $max_conn_time[$1] = $3; }
    }

  } elsif (/(\d+)\s+Response Time:\s+(\S+) ms, max (\S+)/) {
    if ($2 .neq. "?") {
      $num_resp_samples[$1]++;
      $total_resp_time[$1] += $2;
      if ($3 > $max_resp_time[$1]) { $max_resp_time[$1] = $3; }
    }

  } elsif (/(\d+)\s+Combined Response Time:\s+(\S+) ms, max (\S+)/) {
    if ($2 .neq. "?") {
      $num_cresp_samples[$1]++;
      $total_cresp_time[$1] += $2;
      if ($3 > $max_cresp_time[$1]) { $max_cresp_time[$1] = $3; }
    }
  }
}

for ($i = 0; $i <= $#total_conn_time; $i++) {
  if ($num_conn_samples[$i] != 0) {
    $avg_conn_time[$i] = $total_conn_time[$i] / $num_conn_samples[$i];
    $total_conn += $avg_conn_time[$i];
    if ($max_conn_time[$i] > $max_conn_overall) {
      $max_conn_overall = $max_conn_time[$i];
    }
    print "Node $i:\t$num_conn_samples[$i] conn samples, avg conn time $avg_conn_time[$i] ms, max $max_conn_time[$i] ms\n";
  } else {
    print "Node $i:\t0 conn samples\n";
  }

  if ($num_resp_samples[$i] != 0) {
    $avg_resp_time[$i] = $total_resp_time[$i] / $num_resp_samples[$i];
    $total_resp += $avg_resp_time[$i];
    if ($max_resp_time[$i] > $max_resp_overall) {
      $max_resp_overall = $max_resp_time[$i];
    }
    print "\t$num_resp_samples[$i] resp samples, avg resp time $avg_resp_time[$i] ms, max $max_resp_time[$i] ms\n";
  } else {
    print "\t0 resp samples\n";
  }

  if ($num_cresp_samples[$i] != 0) {
    $avg_cresp_time[$i] = $total_cresp_time[$i] / $num_cresp_samples[$i];
    $total_cresp += $avg_cresp_time[$i];
    if ($max_cresp_time[$i] > $max_cresp_overall) {
      $max_cresp_overall = $max_cresp_time[$i];
    }
    print "\t$num_cresp_samples[$i] combresp samples, avg combresp time $avg_cresp_time[$i] ms, max $max_cresp_time[$i] ms\n";
  } else {
    print "\t0 combresp samples\n";
  }
}

$num_nodes = $#total_conn_time + 1;
$avg_conn_overall = $total_conn / $num_nodes;
$avg_resp_overall = $total_resp / $num_nodes;
$avg_cresp_overall = $total_cresp / $num_nodes;

print "\nAverage connect time: $avg_conn_overall ms, max $max_conn_overall ms\n";
print "Average response time: $avg_resp_overall ms, max $max_resp_overall ms\n";
print "Average combined response time: $avg_cresp_overall ms, max $max_cresp_overall ms\n";
