/* 
 * Copyright (c) 2000 by Matt Welsh and The Regents of the University of 
 * California. All rights reserved.
 *
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose, without fee, and without written agreement is
 * hereby granted, provided that the above copyright notice and the following
 * two paragraphs appear in all copies of this software.
 * 
 * IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR
 * DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING OUT
 * OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF THE UNIVERSITY OF
 * CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
 * ON AN "AS IS" BASIS, AND THE UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO
 * PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * Author: Matt Welsh <mdw@cs.berkeley.edu>
 * 
 */

/* 
 * This program creates a number of outgoing socket connections
 * (each handled by its own thread), and writes bursts of packets to
 * each socket. After each burst a second burst of messages is received, 
 * and the next burst is sent. Using the -q option makes the client 
 * only open a connection, and not send any data; this is used 
 * to measure the overhead of idle connections to a server.
 */

import seda.util.MDWUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;

public class MultiClientThreaded extends Thread {

  private static final boolean DEBUG = false;
  private static final int PORTNUM = 5721;

  private static final boolean OVERALL_REPORT = true;
  private static final boolean VERBOSE_REPORT = false;
  private static final boolean HISTOGRAM_REPORT = true;

  private static String REMOTE_HOST;
  private static int NUM_CONNECTIONS;
  private static int SEND_MESSAGE_SIZE;
  private static int RECV_MESSAGE_SIZE;
  private static int SEND_BURST_SIZE;
  private static int RECV_BURST_SIZE;
  private static boolean QUIET;

  // Time in ms between reporting measurements
  private static final int BENCH_DELAY = 5000;

  // Time in ms to delay before starting
  private static final int SLEEP_TIME = 5000;

  // Number of bench samples before we exit; if zero, run forever
  private static final int NUMBER_RUNS = 20;
  
  private Socket sock;
  private int tnum;
  private boolean benchthread;

  private static Object lock;
  private static int num_connections = 0;
  private static long max_connect_time = 0;
  private static long total_connect_time = 0;
  private static int num_completions = 0;
  private static int num_bytes = 0;
  private static long max_response_time = 0;
  private static long total_response_time = 0;
  private static Hashtable conn_time_hist, resp_time_hist;
  private static final int CONN_HIST_BUCKETSIZE = 10;
  private static final int RESP_HIST_BUCKETSIZE = 10;
  private static int numBenchRuns = 0;
  private static int clientNumBurstsSent[];
  private static int clientNumBurstsReceived[];

  public MultiClientThreaded(int tnum) {
    this.tnum = tnum;
    this.benchthread = false;
  }

  public MultiClientThreaded() {
    this.benchthread = true;
  }

  public void run() {
    if (benchthread) doBenchThread();
    else doClientThread();
  }

  private static void printResults(String msg, int numiters, int message_size, long t1, long t2) {
    double usec = (t2-t1)*1.0e3;
    double usecper = (usec/numiters);
    double megabits = (message_size*numiters*8.0)/(1024*1024);
    double bw = (megabits * 1.0e3)/(t2-t1);

    System.out.println(msg+":");
    System.out.println("\t"+numiters+" "+message_size+"-byte messages sent in "+usec+" usec, or "+usecper+" usec/iter.");
    System.out.println("\t"+bw+" megabits/sec send bandwidth.");
  }

  private void doBenchThread() {
    long t1, t2;
    long num_conns, total_conn, max_conn;
    long num_comps, num_bts, total_resp, max_resp;

    while (true) {

      t1 = System.currentTimeMillis();

      MDWUtil.sleep(BENCH_DELAY);

      if (OVERALL_REPORT) {
	synchronized (lock) {
	  t2 = System.currentTimeMillis();

	  num_conns = num_connections; num_connections = 0;
	  total_conn = total_connect_time; total_connect_time = 0;
	  max_conn = max_connect_time; max_connect_time = 0;

	  num_comps = num_completions; num_completions = 0;
          num_bts = num_bytes; num_bytes = 0;
	  total_resp = total_response_time; total_response_time = 0;
	  max_resp = max_response_time; max_response_time = 0;
	}

	double sec = (t2-t1) * 1.0e-3;

	double conns_per_sec = (double)num_conns*1.0 / sec;
	double comps_per_sec = (double)num_comps*1.0 / sec;
	double bytes_per_sec = (double)num_bts*1.0 / sec;
	double mbit_per_sec = (bytes_per_sec * 8.0) / (1024.0 * 1024.0);
	double avg_conn_time = (double)total_conn*1.0 / num_conns;
	double avg_resp_time = (double)total_resp*1.0 / num_comps;

	System.err.println("Connect Rate:\t"+MDWUtil.format(conns_per_sec)+" connections/sec, "+num_conns+" conns");
	System.err.println("Overall rate:\t"+MDWUtil.format(comps_per_sec)+" completions/sec");
	System.err.println("Bandwidth:\t"+MDWUtil.format(bytes_per_sec)+" bytes/sec, "+MDWUtil.format(mbit_per_sec)+" Mbit/sec");
	System.err.println("Connect Time:\t"+MDWUtil.format(avg_conn_time)+" ms, max "+MDWUtil.format(max_conn)+" ms");
	System.err.println("Response Time:\t"+MDWUtil.format(avg_resp_time)+" ms, max "+MDWUtil.format(max_resp)+" ms\n");
      }

      if (HISTOGRAM_REPORT) {

	synchronized (conn_time_hist) {
	  Enumeration e = conn_time_hist.keys();
	  while (e.hasMoreElements()) {
	    Integer bucket = (Integer)e.nextElement();
	    int time = bucket.intValue() * CONN_HIST_BUCKETSIZE;
	    int val = ((Integer)conn_time_hist.get(bucket)).intValue();
	    System.err.println("CT "+time+" ms "+val+" count");
	  }
	  conn_time_hist.clear();
	  System.err.println("\n");
	}
	synchronized (resp_time_hist) {
	  Enumeration e = resp_time_hist.keys();
	  while (e.hasMoreElements()) {
	    Integer bucket = (Integer)e.nextElement();
	    int time = bucket.intValue() * RESP_HIST_BUCKETSIZE;
	    int val = ((Integer)resp_time_hist.get(bucket)).intValue();
	    System.err.println("RT "+time+" ms "+val+" count");
	  }
	  resp_time_hist.clear();
	  System.err.println("\n");
	}
      }

      numBenchRuns++;
      if (numBenchRuns == NUMBER_RUNS) {
	if (OVERALL_REPORT) {
	  System.err.println("Fairness report:");
	  synchronized (lock) {
	    int totalSent = 0, totalReceived = 0;
	    double avgSent, avgReceived;
	    for (int i = 0; i < NUM_CONNECTIONS; i++) {
	      totalSent += clientNumBurstsSent[i];
	      totalReceived += clientNumBurstsReceived[i];
	    }
	    avgSent = totalSent / NUM_CONNECTIONS;
	    avgReceived = totalReceived / NUM_CONNECTIONS;
	    System.err.println("Bursts sent: "+totalSent+" total, "+avgSent+" average");
	    System.err.println("Bursts received: "+totalReceived+" total, "+avgReceived+" average");
	    for (int i = 0; i < NUM_CONNECTIONS; i++) {
	      System.err.println("Client "+i+" "+clientNumBurstsSent[i]+" sent, "+clientNumBurstsReceived[i]+" received");
	    }
	  }
	}
	System.err.println("Benchmark Finished.\n");
	System.exit(0);
      }

    }
  }

  private void doClientThread() {
    long tconn1 = 0, tconn2 = 0;
    long total_lat = 0, total_conn_lat = 0;

    try {

      // Sleep for random time before making connection
      Random r = new Random();
      int s = (Math.abs(r.nextInt()) % SLEEP_TIME) + SLEEP_TIME;
      try {
	System.err.println("Client thread "+tnum+" doing initial sleep "+s+"ms");
	Thread.sleep(s);
      } catch (InterruptedException ie) {
	// Ignore
      }

      tconn1 = System.currentTimeMillis();
      sock = new Socket(REMOTE_HOST, PORTNUM);
      tconn2 = System.currentTimeMillis();

      if (OVERALL_REPORT || HISTOGRAM_REPORT) {
	long conntime = tconn2 - tconn1;
	if (OVERALL_REPORT) {
	  synchronized (lock) {
	    if (conntime > max_connect_time) max_connect_time = conntime;
	    total_connect_time += conntime;
	    num_connections++;
	  }
	}
	if (HISTOGRAM_REPORT) {
	  Integer ct = new Integer((int)(conntime / CONN_HIST_BUCKETSIZE));
	  synchronized (conn_time_hist) {
	    Integer val = (Integer)conn_time_hist.remove(ct);
	    if (val == null) {
	      conn_time_hist.put(ct, new Integer(1));
	    } else {
	      val = new Integer(val.intValue() + 1);
	      conn_time_hist.put(ct, val);
	    }
	  }
	}
      }

      Thread.yield(); // only accomplishes delay

      if (QUIET) {
	System.err.println("Client thread "+tnum+" started");
	while (true) {
	  try {
	    Thread.sleep(100000);
	  } catch (InterruptedException ie) {
	    // Ignore
	  }
	}
      } else {
	// Sleep so all the connections go through
	long t1, t2;
	t1 = System.currentTimeMillis();
	s = (Math.abs(r.nextInt()) % SLEEP_TIME) + SLEEP_TIME;
	while (System.currentTimeMillis() < (t1+s)) {
	  try {
	    System.err.println("Client thread "+tnum+" doing second sleep "+s+"ms");
	    Thread.sleep(s);
	  } catch (InterruptedException ie) {
	    // Ignore
	  }
	}
	System.err.println("Client thread "+tnum+" started");
	doBandwidth();
      }
      sock.close();
    } catch (Exception e) {
      System.err.println("Client thread "+tnum+" got exception: "+e.getMessage());
      e.printStackTrace();
    }
  }

  private void doBandwidth() throws IOException {

    OutputStream os = sock.getOutputStream();
    InputStream is = sock.getInputStream();
    byte barr[] = new byte[SEND_MESSAGE_SIZE];
    byte barr2[] = new byte[RECV_MESSAGE_SIZE];
    int i = 0;
    long t1, t2;
    long treq1, treq2;

    for (i = 0; i < barr.length; i++) {
      barr[i] = (byte)(i & 0xff);
    }

    System.out.println("Starting client thread "+tnum+": send message size "+SEND_MESSAGE_SIZE+", send burst "+SEND_BURST_SIZE+", recv message size "+RECV_MESSAGE_SIZE+", recv burst "+RECV_BURST_SIZE);

    t1 = System.currentTimeMillis();
    i = 0;
    while (true) {
      if (DEBUG) System.err.println("["+i+"] Sender "+tnum+": Sending message...");
      treq1 = System.currentTimeMillis();
      os.write(barr); 
      os.flush();
      
      Thread.yield(); // only accomplishes delay
      i++;

      if ((i % SEND_BURST_SIZE) == 0) {
	synchronized(lock) {
	  clientNumBurstsSent[tnum]++;
	}
        if (DEBUG) System.err.println("Receiving burst...");
        int m = 0;
	while (m < RECV_BURST_SIZE) {
	  int n = 0;
          while (n < barr2.length) {
	    int c = is.read(barr2, n, (barr2.length - n));
	    n += c;
	    if (DEBUG) System.err.println("  Received "+n+" bytes, total "+n);
          }
	  m++;
	  if (DEBUG) System.err.println("  Got msg "+m+" out of "+RECV_BURST_SIZE);
        }
        treq2 = System.currentTimeMillis();
	synchronized(lock) {
	  clientNumBurstsReceived[tnum]++;
	}

	if (OVERALL_REPORT || HISTOGRAM_REPORT) {
	  long resptime = treq2 - treq1;
	  if (OVERALL_REPORT) {
	    synchronized (lock) {
	      if (resptime > max_response_time) max_response_time = resptime;
	      total_response_time += resptime;
	      num_completions++;
	      num_bytes += RECV_MESSAGE_SIZE * RECV_BURST_SIZE;
	    }
	  }
	  if (HISTOGRAM_REPORT) {
	    Integer rt = new Integer((int)(resptime / RESP_HIST_BUCKETSIZE));
	    synchronized (resp_time_hist) {
	      Integer val = (Integer)resp_time_hist.remove(rt);
	      if (val == null) {
		resp_time_hist.put(rt, new Integer(1));
	      } else {
		val = new Integer(val.intValue() + 1);
		resp_time_hist.put(rt, val);
	      }
	    }
	  }
	}

	t2 = System.currentTimeMillis();
	if (VERBOSE_REPORT) {
  	  printResults("Client thread "+tnum, SEND_BURST_SIZE, SEND_MESSAGE_SIZE, t1, t2);
	}
	t1 = System.currentTimeMillis();
      }
    }
  }

  public static void main(String args[]) {

    if (HISTOGRAM_REPORT) {
      conn_time_hist = new Hashtable();
      resp_time_hist = new Hashtable();
    }

    try {
      if ((args.length < 6) || (args.length > 7)) {
	System.err.println("Usage: java MultiClientThreaded [-q] <remote host> <num clients> <send message size> <send burst size> <recv message size> <recv burst size>");
	System.err.println("\t-q\tDo not send any messages (be quiet)");
	System.exit(-1);
      }

      int n = 0;
      if (args[0].equals("-q")) {
	QUIET = true;
	n++;
      } else {
	QUIET = false;
      }

      REMOTE_HOST = args[n+0];
      NUM_CONNECTIONS = Integer.valueOf(args[n+1]).intValue();
      SEND_MESSAGE_SIZE = Integer.valueOf(args[n+2]).intValue();
      SEND_BURST_SIZE = Integer.valueOf(args[n+3]).intValue();
      RECV_MESSAGE_SIZE = Integer.valueOf(args[n+4]).intValue();
      RECV_BURST_SIZE = Integer.valueOf(args[n+5]).intValue();

      lock = new Object();
      MultiClientThreaded benchthread = null;

      if (OVERALL_REPORT || HISTOGRAM_REPORT) {
	benchthread = new MultiClientThreaded();
	clientNumBurstsSent = new int[NUM_CONNECTIONS];
	clientNumBurstsReceived = new int[NUM_CONNECTIONS];
      }


      if (QUIET) {
        Random r = new Random();
        int s = Math.abs(r.nextInt()) % 5000;
        try {
    	  System.err.println("Doing initial sleep "+s+"ms");
   	  Thread.sleep(s);
    	} catch (InterruptedException ie) {
  	  // Ignore
   	}

	Socket sarr[] = new Socket[NUM_CONNECTIONS];
	for (int i = 0; i < NUM_CONNECTIONS; i++) {
	  sarr[i] = new Socket(REMOTE_HOST, PORTNUM);
          try {
   	    Thread.sleep(10);
      	  } catch (InterruptedException ie) {
  	    // Ignore
   	  }
	}

	System.err.println("Done creating connections");
	while (true) {
	  try {
	    Thread.sleep(100000);
	  } catch (InterruptedException ie) {
	    // Ignore
	  }
	}

      } else {
	for (int i = 0; i < NUM_CONNECTIONS; i++) {
  	  MultiClientThreaded mc = new MultiClientThreaded(i);
  	  mc.start();
   	}
        if (OVERALL_REPORT || HISTOGRAM_REPORT) {
	  // Wait until all threads have started communicating
	  MDWUtil.sleep(20000);
  	  benchthread.start();
        }
      }
      
    } catch (Exception e) {
      System.out.println("MultiClientThreaded: Got exception: "+e.getMessage());
      e.printStackTrace();
    }
  }
}

