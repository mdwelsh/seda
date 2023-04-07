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

/* This is a simple test program which demonstrates the use of the
 * Sandstorm aSocket interface. It consists of a sender and receiver,
 * which simply ping-pong messages back and forth between each other.
 * It can be used as a basic network round-trip-time benchmark.
 */

import seda.nbio.NonblockingOutputStream;
import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.lib.aSocket.*;
import seda.sandStorm.main.Sandstorm;
import seda.sandStorm.main.SandstormConfig;
import seda.util.Tracer;

import java.io.IOException;
import java.net.UnknownHostException;

public class Pingpong {

  String peer;
  boolean sending;
  QueueIF comp_q = null;
  SinkIF sink;
  ATcpConnection conn;
  ATcpClientSocket clisock;
  ATcpServerSocket servsock;

  private static final boolean DEBUG = false;
  private static final boolean VERBOSE = true;
  private static final boolean PROFILE = false;
  private static final boolean USE_NIO = false;

  // If true, receiver sends a message as well (so one is always in flight)
  private static final boolean EXCHANGE = false;

  // If true, do writes directly on outgoing socket
  private static final boolean DIRECT_WRITE = false;

  // If true, do more careful measurements (for benchmarking)
  private static final boolean BENCH = true;

  // Only valid if BENCH is true
  private static final int NUM_MEASUREMENTS = 5;
  private static final int NUM_MSGS_TO_SKIP = 100;
  private static final int NUM_MSGS_PER_MEASUREMENT = 100;
  private static long measurements[];

  private static final int PORTNUM = 5957;
  private static int MSG_SIZE;

  private static Tracer tracer = new Tracer("Pingpong");

  public Pingpong(String peer, boolean sending) {
    this.peer = peer;
    this.sending = sending;
  }

  public void setup() throws IOException, UnknownHostException {
    boolean connected = false;
    QueueElementIF fetched[];

    if (BENCH) measurements = new long[NUM_MEASUREMENTS];
    comp_q = new FiniteQueue();

    if (sending) {
      System.err.println("Pingpong: Connecting to "+peer+":"+PORTNUM);
      clisock = new ATcpClientSocket(peer, PORTNUM, comp_q);
    } else {
      System.err.println("Pingpong: Listening on port "+PORTNUM);
      servsock = new ATcpServerSocket(PORTNUM, comp_q);
    }

    while (!connected) {

      /* Wait for connection */
      if (DEBUG) System.err.println("Pinpong: Waiting for connection to complete");
      while ((fetched = comp_q.blocking_dequeue_all(0)) == null) ;

      if (fetched.length != 1) throw new IOException("Got more than one event on initial fetch?");

      if (fetched[0] instanceof aSocketErrorEvent) {
	if (!BENCH) {
	  System.err.println("Still trying to connect: "+((aSocketErrorEvent)fetched[0]).toString());
	}
	try {
	  Thread.sleep(100);
	} catch (InterruptedException ie) {
	  // Do nothing
	}
      } else if (fetched[0] instanceof ATcpListenSuccessEvent) {
	// Ignore
      } else {
	conn = (ATcpConnection)fetched[0];
	conn.startReader(comp_q);
	sink = (SinkIF)conn;
	connected = true;
	System.err.println("Pingpong: Got connection "+conn);
      }
    }

  }

  public void doIt() throws SinkClosedException, IOException {
    int i;
    int n = 0;

    if (DEBUG) System.err.println("Pinpong: doIt called");

    // Allocate buffer for message
    byte barr[] = new byte[MSG_SIZE];
    BufferElement buf = new BufferElement(barr);
    // Fill in the buffer with some data
    for (i = 0; i < MSG_SIZE; i++) {
      barr[i] = (byte)(i & 0xff);
    }
    if (DEBUG) System.err.println("Pinpong: initialized packet");

    QueueElementIF fetched[];
    long before, after;

    if (EXCHANGE || sending) {
      if (DEBUG) System.err.println("Pingpong: Sending first message");
      try {
	// Enqueue the message
	if (PROFILE) tracer.trace("send first enqueue");
  	sink.enqueue(buf);
	if (PROFILE) tracer.trace("send first enqueue done");
	Thread.yield(); // only accomplishes delay
      } catch (SinkException se) {
	System.err.println("Warning: Got SinkException on enqueue: "+se.getMessage());
      }
      if (DEBUG) System.err.println("Pingpong: Sent first message");
    }

    int total_size = 0;
    int m = 0;
    
    while (true) {

      // Reset stats after warmup
      if (PROFILE && (n == 10)) Tracer.resetAll();
      
      // Block on incoming event queue waiting for events
      if (PROFILE) tracer.trace("wait for dequeue");
      if (DEBUG) System.err.println("\n\n\nPingpong: Waiting for dequeue...");
      while ((fetched = comp_q.blocking_dequeue_all(0)) == null) ;

      if (PROFILE) tracer.trace("dequeue done");
      if (DEBUG) System.err.println("Pingpong: Got event: "+fetched);

      for (i = 0; i < fetched.length; i++) {
        if (fetched[i] instanceof aSocketErrorEvent) {
          throw new IOException("Got error! "+((aSocketErrorEvent)fetched[i]).getMessage());
        }

	// Received a packet
	if (fetched[i] instanceof ATcpInPacket) {

          ATcpInPacket pkt = (ATcpInPacket)fetched[i];
          int size = pkt.size();

          if (PROFILE) tracer.trace("process inpkt");
	  if (DEBUG) System.err.println("Got packet size="+size);

          total_size += size;
	  if (total_size == MSG_SIZE) {
	    n++;
	    if (!sending && ((n % 10) == 0)) System.err.print(".");

	    // If performing timing measurements...
	    if (BENCH && sending) {
	      if (n == NUM_MSGS_TO_SKIP) {
	        // Skip initial bursts of packets to warm up the pipeline
	        measurements[0] = System.currentTimeMillis();
		m = 1;
	      } else {
	        if (((n - NUM_MSGS_TO_SKIP) % NUM_MSGS_PER_MEASUREMENT) == 0) {
		  measurements[m] = System.currentTimeMillis();
		  m++;
		  if (m == NUM_MEASUREMENTS) {
		    printMeasurements();
	            if (sending) return;
		  }
		}
	      }
	    } 

	    // After 500 messages print out the time
	    if (!BENCH && (n % 500 == 0)) {
	      after = System.currentTimeMillis();
	      if (VERBOSE) printTime(before, after, 500, MSG_SIZE);
	      before = after;
	    }

            // Send new message
	    if (DIRECT_WRITE) {
	      try {
		NonblockingOutputStream nbos = (NonblockingOutputStream)conn.getSocket().getOutputStream();
		int n2 = 0;
		while (n2 < buf.data.length) {
		  n2 += nbos.nbWrite(buf.data, n2, buf.data.length - n2);
		}
	      } catch (Exception e) {
	        System.err.println("Warning: Got Exception on direct write: "+e);
	      }
	    } else {
	      try {
		if (PROFILE) tracer.trace("send enqueue");
		sink.enqueue(buf);
		if (PROFILE) tracer.trace("send enqueue done");
		Thread.yield(); // only accomplishes delay
	      } catch (SinkException se) {
		System.err.println("Warning: Got SinkException on enqueue: "+se);
	      }
	    }

	    total_size = 0;
	  }

	} else if (fetched[i] instanceof SinkDrainedEvent) {
	  if (DEBUG) System.err.println("Got SinkDrainedEvent!");
	} else if (fetched[i] instanceof SinkClosedEvent) {
	  System.err.println("Got SinkClosedEvent - quitting");
	  return;
	} else {
	  throw new IOException("Sender got unknown comp_q event: "+fetched[i].toString());
	}
      }
    }
  }

  private static void printMeasurements() {
    int m;
    System.err.println("# size\t time(ms)\t rtt(usec)\t mbps");
    for (m = 1; m < NUM_MEASUREMENTS; m++) {
      long t1 = measurements[m-1];
      long t2 = measurements[m];
      long diff = t2-t1;
      double iters_per_ms = (double) NUM_MSGS_PER_MEASUREMENT / (double) diff;
      double iters_per_sec = iters_per_ms * 1000.0;
      double rtt_usec = (diff * 1000.0)/((double)NUM_MSGS_PER_MEASUREMENT);
      double mbps = (NUM_MSGS_PER_MEASUREMENT * MSG_SIZE * 8.0)/((double)diff * 1.0e3);
      System.err.println(MSG_SIZE+"\t "+diff+"\t "+rtt_usec+"\t "+mbps);
    }
  }

  private static void printTime(long t1, long t2, int numiters, int msg_size) {
    long diff = t2-t1;
    double iters_per_ms = (double) numiters / (double) diff;
    double iters_per_sec = iters_per_ms * 1000.0;
    double rtt_usec = (diff * 1000.0)/((double)numiters);
    double mbps = (numiters * msg_size * 8.0)/((double)diff * 1.0e3);

    System.err.println( numiters + " iterations in " + diff +
                        " milliseconds = " + iters_per_sec
                        + " iterations per second" );
    System.err.println("\t"+rtt_usec+" usec RTT, "+mbps+" mbps bandwidth");
  }

  private static void usage() {
    System.err.println("usage: Pingpong [send|recv] <remote_hostname> <msgsize>");
    System.exit(1);
  }

  public static void main(String args[]) {
    Pingpong np;
    boolean sending = false;

    if (args.length != 3) usage();

    if (args[0].equals("send")) sending = true;
    MSG_SIZE = Integer.decode(args[2]).intValue();

    try {
      SandstormConfig cfg = new SandstormConfig();
      if (USE_NIO) cfg.putString("global.aSocket.provider", "NIO");
      Sandstorm ss = new Sandstorm(cfg);

      if (DEBUG) System.err.println("Pingpong: Creating pingpong object...");
      np = new Pingpong(args[1], sending);
      if (DEBUG) System.err.println("Pingpong: Calling setup...");
      np.setup();
      np.doIt();

      if (PROFILE) Tracer.dumpAll();

      System.exit(0);

    } catch (Exception e) {
      if (VERBOSE) System.err.println("Pingpong.main() got exception: "+e);
      if (VERBOSE) e.printStackTrace();
      System.exit(0);
    }
  }

}
