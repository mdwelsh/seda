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
 * This program creates a number of outgoing socket connections and 
 * writes bursts of packets to each socket. After each burst a second 
 * burst of messages is received, and the next burst is sent. 
 * Using the -q option makes the client only open a connection, and not 
 * send any data; this is used to measure the overhead of idle connections 
 * to a server.
 */

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.QueueIF;
import seda.sandStorm.api.SinkClosedEvent;
import seda.sandStorm.api.SinkClosedException;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.core.ssTimer;
import seda.sandStorm.lib.aSocket.ATcpClientSocket;
import seda.sandStorm.lib.aSocket.ATcpConnection;
import seda.sandStorm.lib.aSocket.ATcpInPacket;
import seda.util.MDWUtil;

import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

public class MultiClient {

  private static final boolean DEBUG = false;
  private static final int PORTNUM = 5721;

  private static final boolean OVERALL_REPORT = true;
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
  private static final int NUMBER_RUNS = 10;

  private static BufferElement sendbuf;
  private static QueueIF comp_q;
  private static Vector connections;
  private static ssTimer timer;

  private static int num_connections = 0;
  private static long total_connect_time = 0;
  private static int num_completions = 0;
  private static int num_bytes_read = 0;
  private static long total_response_time = 0;
  private static long max_response_time = 0;
  private static Hashtable resp_time_hist;
  private static final int RESP_HIST_BUCKETSIZE = 10;
  private static int numBenchRuns = 0;

  public MultiClient() {
    connections = new Vector();
    resp_time_hist = new Hashtable();
    timer = new ssTimer();
  }

  private void printStats(long time_elapsed) {
    long num_comps, num_bts, total_resp, max_resp;


    if (OVERALL_REPORT) {
      num_comps = num_completions; num_completions = 0;
      num_bts = num_bytes_read; num_bytes_read = 0;
      total_resp = total_response_time; total_response_time = 0;
      max_resp = max_response_time; max_response_time = 0;

      double sec = time_elapsed * 1.0e-3;

      double comps_per_sec = (double)num_comps*1.0 / sec;
      double bytes_per_sec = (double)num_bts*1.0 / sec;
      double mbit_per_sec = (bytes_per_sec * 8.0) / (1024.0 * 1024.0);
      double avg_resp_time = (double)total_resp*1.0 / num_comps;

      System.err.println("Overall rate:\t"+MDWUtil.format(comps_per_sec)+" completions/sec");
      System.err.println("Bandwidth:\t"+MDWUtil.format(bytes_per_sec)+" bytes/sec ("+MDWUtil.format(mbit_per_sec)+" Mbit/sec)");
      System.err.println("Response Time:\t"+MDWUtil.format(avg_resp_time)+" ms, max "+MDWUtil.format(max_resp)+" ms\n");
    }

    if (HISTOGRAM_REPORT) {

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

    numBenchRuns++;
    if (numBenchRuns == NUMBER_RUNS) {
      if (OVERALL_REPORT) {
	System.err.println("Fairness report:");
	int totalSent = 0, totalReceived = 0;
     	double avgSent, avgReceived;
	for (int i = 0; i < connections.size(); i++) {
	  ConnState cs = (ConnState)connections.elementAt(i);
	  totalSent += cs.num_sent;
	  totalReceived += cs.num_received;
	}
	avgSent = totalSent / connections.size();
	avgReceived = totalReceived / connections.size();
	System.err.println("Bursts sent: "+totalSent+" total, "+avgSent+" average");
	System.err.println("Bursts received: "+totalReceived+" total, "+avgReceived+" average");
	for (int i = 0; i < connections.size(); i++) {
	  ConnState cs = (ConnState)connections.elementAt(i);
	  System.err.println("Client "+i+" "+cs.num_sent+" sent, "+cs.num_received+" received");
	}
      }

      System.err.println("Benchmark Finished.\n");
      for (int i = 0; i < connections.size(); i++) {
	ConnState cs = (ConnState)connections.elementAt(i);
	cs.close();
      }
      System.exit(0);
    }
  }

  class ConnState implements QueueElementIF {

    ATcpConnection conn;
    int num_sent, num_received;
    int cur_offset, cur_length_target;
    long burst_sent_time;

    ConnState(ATcpConnection conn) {
      this.conn = conn;
      this.num_sent = 0;
      this.num_received = 0;
      this.cur_offset = 0;
      this.cur_length_target = RECV_MESSAGE_SIZE*RECV_BURST_SIZE;
      connections.addElement(this);
    }

    boolean readDone(int size) {
      if (DEBUG) System.err.println("Adding "+size+" bytes to "+this.cur_offset);
      num_bytes_read += size;
      this.cur_offset += size;
      if (this.cur_offset == this.cur_length_target) {
	num_received++;
	num_completions++;

	// Record response time
	long t2 = System.currentTimeMillis();
	long resptime = (t2 - burst_sent_time);
	if (resptime > max_response_time) max_response_time = resptime;
	total_response_time += resptime;
	if (HISTOGRAM_REPORT) {
	  Integer rt = new Integer((int)(resptime / RESP_HIST_BUCKETSIZE));
	  Integer val = (Integer)resp_time_hist.remove(rt);
	  if (val == null) {
	    resp_time_hist.put(rt, new Integer(1));
	  } else {
	    val = new Integer(val.intValue() + 1);
	    resp_time_hist.put(rt, val);
	  }
	}   

	return true;
      } else {
	return false;
      }
    }

    void reset() {
      this.cur_offset = 0;
      this.cur_length_target = RECV_MESSAGE_SIZE*RECV_BURST_SIZE;
    }

    void sendBurst() {
      burst_sent_time = System.currentTimeMillis();
      for (int i = 0; i < SEND_BURST_SIZE; i++) {
	if (!conn.enqueue_lossy(sendbuf)) {
	  System.err.println("WARNING: Could not enqueue message onto conn "+conn);
	}
      }
      num_sent++;
    }

    void close() {
      try {
	conn.close(comp_q);
      } catch (SinkClosedException sce) {
	System.err.println("WARNING: Connection "+conn+" already closed");
      }
    }
  }

  private void doIt() {

    Random rand = new Random();
    comp_q = new FiniteQueue();
    byte sendmsg[] = new byte[SEND_MESSAGE_SIZE];
    for (int i = 0; i < sendmsg.length; i++) {
      sendmsg[i] = (byte)(i & 0xff);
    }
    sendbuf = new BufferElement(sendmsg);
    QueueElementIF fetched[];
    long time_connect_requested, t1, t2;

    // Sleep before starting
    long t = (Math.abs(rand.nextInt()) % 5000) + 5000;
    MDWUtil.sleep(t);

    time_connect_requested = System.currentTimeMillis();

    for (int i = 0; i < NUM_CONNECTIONS; i++) {
      try {
	ATcpClientSocket sock = new ATcpClientSocket(REMOTE_HOST, PORTNUM, comp_q);
      } catch (UnknownHostException e) {
	System.err.println("Got UnknownHostException: "+e);
	return;
      }

      // Pause between connection requests
      t = (Math.abs(rand.nextInt()) % 20) + 20;
      MDWUtil.sleep(t);
    }

    t1 = System.currentTimeMillis();

    while (true) {
      while ((fetched = comp_q.blocking_dequeue_all(-1)) == null) ;

      for (int i = 0; i < fetched.length; i++) {

	if (fetched[i] instanceof ATcpConnection) {
	  // New connection
	  System.err.println("Established connection "+num_connections);
	  ATcpConnection conn = (ATcpConnection)fetched[i];
	  num_connections++;

	  if (num_connections == NUM_CONNECTIONS) {
	    long time_connect_done = System.currentTimeMillis();
	    total_connect_time = time_connect_done - time_connect_requested;
	    double avg_connect_time = total_connect_time / NUM_CONNECTIONS;
	    System.err.println("All connections received, avg conn time "+MDWUtil.format(avg_connect_time)+" ms");
	  }

	  conn.startReader(comp_q);
	  ConnState cs = new ConnState(conn);
	  conn.userTag = cs;

	  if (!QUIET) {
	    // Start the burst after SLEEP_TIME
	    t = (Math.abs(rand.nextInt()) % SLEEP_TIME) + SLEEP_TIME;
	    System.err.println("Pausing connection for "+t+" ms");
	    timer.registerEvent(t, cs, comp_q);
	  }

	} else if (fetched[i] instanceof ConnState) {
	  // Connection is ready for first burst
	  ConnState cs = (ConnState)fetched[i];
	  System.err.println("Starting connection");
	  cs.sendBurst();

	} else if (fetched[i] instanceof ATcpInPacket) {
	  // Incoming packet
	  ATcpInPacket pkt = (ATcpInPacket)fetched[i];
	  ATcpConnection conn = pkt.getConnection();

	  ConnState cs = (ConnState)conn.userTag;
	  if (cs.readDone(pkt.size())) {
	    cs.sendBurst();
	    cs.reset();
	  }

	} else if (fetched[i] instanceof SinkClosedEvent) {
	  num_connections--;
	  SinkClosedEvent sce = (SinkClosedEvent)fetched[i];
	  ATcpConnection conn = (ATcpConnection)sce.sink;
	  ConnState cs = (ConnState)conn.userTag;
	  connections.removeElement(cs);
	  conn.userTag = null;

	} else {
	  System.err.println("Got unknown comp_q event: "+fetched[i].toString());
	}

	// Print stats
	t2 = System.currentTimeMillis();
	if ((t2 - t1) >= BENCH_DELAY) {
	  printStats((t2-t1));
	  t1 = t2;
	}
      }
    }
  }

  public static void main(String args[]) {

    try {
      if ((args.length < 6) || (args.length > 7)) {
	System.err.println("Usage: java MultiClient [-q] <remote host> <num clients> <send message size> <send burst size> <recv message size> <recv burst size>");
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
      
      if (!QUIET) {
	System.out.println("Starting benchmark: num connections "+NUM_CONNECTIONS+", send message size "+SEND_MESSAGE_SIZE+", send burst "+SEND_BURST_SIZE+", recv message size "+RECV_MESSAGE_SIZE+", recv burst "+RECV_BURST_SIZE);
      } else {
	System.err.println("Starting benchmark: num connections "+NUM_CONNECTIONS+", quiet run");
      }

      MultiClient mc = new MultiClient();
      mc.doIt();

    } catch (Exception e) {
      System.out.println("MultiClient: Got exception: "+e.getMessage());
      e.printStackTrace();
    }
  }
}

