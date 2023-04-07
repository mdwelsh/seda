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
 * Sandstorm UDP aSocket interface. It consists of a sender and receiver,
 * which simply ping-pong messages back and forth between each other.
 * It can be used as a basic network round-trip-time benchmark.
 */

import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.lib.aSocket.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class PingpongUDP {
  
  String peer;
  boolean sending;
  FiniteQueue comp_q = null;
  SinkIF sink;
  AUdpSocket sock;
  InetAddress peer_addr;

  private static final boolean DEBUG = true;
  private static final boolean VERBOSE = true;

  // If true, use AUdpSocket.connect() to create default send address
  private static final boolean CONNECT = false;

  // If true, do more careful measurements (for benchmarking)
  private static final boolean BENCH = true;

  // Number of redundant packets to send 
  private static final int BURST_SIZE = 5;

  // Only valid if BENCH is true
  private static final int NUM_MEASUREMENTS = 5;
  private static final int NUM_MSGS_TO_SKIP = 100;
  private static final int NUM_MSGS_PER_MEASUREMENT = 100;
  private static long measurements[];

  private static final int PORTNUM = 5060;
  private static int MSG_SIZE;

  private static int getInt(byte[] data) {
    int sum = 0;
    if(data.length < 4) return -1;
    sum = (0xff000000 & (int)(data[3] << 24)) |
      (0x00ff0000 & (int)(data[2] << 16)) |
      (0x0000ff00 & (int)(data[1] << 8)) |
      (0x000000ff & (int)(data[0] << 0));
    return sum;
  }

  private static void putInt(byte[] data, int p) {
    if(data.length < 4) return;
    data[3] = (byte)((0xff000000 & p) >>24);
    data[2] = (byte)((0x00ff0000 & p) >>16);
    data[1] = (byte)((0x0000ff00 & p) >>8);
    data[0] = (byte)((0x000000ff & p) >>0);
  }

  public PingpongUDP(String peer, boolean sending) throws Exception {
    this.peer = peer;
    this.peer_addr = InetAddress.getByName(this.peer);
    this.sending = sending;
  }

  public void setup() throws IOException, UnknownHostException {
    boolean connected = false;
    QueueElementIF fetched[];

    if (BENCH) measurements = new long[NUM_MEASUREMENTS];
    comp_q = new FiniteQueue();

    if (this.sending) {
        sock = new AUdpSocket(PORTNUM, comp_q);
        if (CONNECT) sock.connect(peer_addr, PORTNUM + 1);
    } else {
        sock = new AUdpSocket(PORTNUM + 1, comp_q);
        if (CONNECT) sock.connect(peer_addr, PORTNUM);
    }

    if (CONNECT) {
      while (!connected) {

	/* Wait for connection */
	if (DEBUG) System.err.println("Pinpong: Waiting for connection to complete");
	while ((fetched = comp_q.blocking_dequeue_all(0)) == null) ;

	if (fetched.length != 1) throw new IOException("Got more than one event on initial fetch?");

	if (fetched[0] instanceof AUdpConnectEvent) {
	  sock.startReader(comp_q);
	  sink = (SinkIF)sock;
	  connected = true;
	  if (DEBUG) System.err.println("PingpongUDP: finished connection");
	} else {
	  throw new IOException("Unknown event returned waiting for connect: "+fetched[0]);
	}
      }

    } else {
      // Not connecting
      sock.startReader(comp_q);
      sink = (SinkIF) sock;
    }


  }

  public void doIt() throws SinkClosedException, IOException {
    int i;
    int n = 0;
    int seqNum = 0, expectedSeqNum = 0;

    if (DEBUG) System.err.println("Pinpong: doIt called");

    // Allocate buffer for message
    byte barr[] = new byte[MSG_SIZE];
    BufferElement buf;

    if (CONNECT) {
      buf = new BufferElement(barr);
    } else {
      buf = new AUdpPacket(barr);
      ((AUdpPacket)buf).address = peer_addr;
      if (sending) { 
	((AUdpPacket)buf).port = PORTNUM+1;
      } else {
	((AUdpPacket)buf).port = PORTNUM;
      }
    }
      
    putInt(barr, seqNum);

    if (DEBUG) System.err.println("Pinpong: initialized packet");

    QueueElementIF fetched[];
    long before, after;

    before = System.currentTimeMillis();
    if (sending) {
      seqNum = 0; expectedSeqNum = 1;
      if (DEBUG) System.err.println("Sender: Sending first message");
      try {
	// Enqueue the message
	for (int k = 0; k < BURST_SIZE; k++) {
	  sink.enqueue(buf);
	}
      } catch (SinkException se) {
	System.err.println("Warning: Got SinkException on enqueue: "+se.getMessage());
      }
      if (DEBUG) System.err.println("Sender: sent first message");
    } else {
      seqNum = 0; expectedSeqNum = 0;
    }

    int total_size = 0;
    int m = 0;
    
    while (true) {
      
      // Block on incoming event queue waiting for events
      if (DEBUG) System.err.println("PingpongUDP: Waiting for dequeue...");
      while ((fetched = comp_q.blocking_dequeue_all(0)) == null) ;


      for (i = 0; i < fetched.length; i++) {
        if (DEBUG) System.err.println("PingpongUDP: Got event: "+fetched[i]);
        if (fetched[i] instanceof aSocketErrorEvent) {
          throw new IOException("Got error! "+((aSocketErrorEvent)fetched[i]).getMessage());
        }

	// Received a packet
	if (fetched[i] instanceof AUdpInPacket) {

          AUdpInPacket pkt = (AUdpInPacket)fetched[i];
          int size = pkt.size();
	  if (DEBUG) System.err.println("Got packet size="+size);

	  if (size == MSG_SIZE) {
	    int recvSeqNum = getInt(pkt.getBytes());
	    if (recvSeqNum == expectedSeqNum) {
	      // Got the expected packet
	      seqNum++; expectedSeqNum++;
	      n++;

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
		      if (sending) System.exit(0);
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

	      // Send new burst
	      try {
		putInt(barr, seqNum);
		for (int k = 0; k < BURST_SIZE; k++) {
		  sink.enqueue(buf);
		}
	      } catch (SinkException se) {
		System.err.println("Warning: Got SinkException on enqueue: "+se);
	      }
	    } else if (recvSeqNum < expectedSeqNum) {
	      // Got duplicated; ignore
	    } else {
	      System.err.println("WARNING: Got unexpected sequence number "+recvSeqNum+", expecting "+expectedSeqNum);
	      System.exit(-1);
	    }

	  } else {
	    System.err.println("WARNING: Got packet size other than expected: "+size);
	    System.exit(-1);
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
    System.err.println("usage: PingpongUDP [send|recv] <remote_hostname> <msgsize>");
    System.exit(1);
  }

  public static void main(String args[]) {
    PingpongUDP np;
    boolean sending = false;

    if (args.length != 3) usage();

    if (args[0].equals("send")) sending = true;
    MSG_SIZE = Integer.decode(args[2]).intValue();
    if (MSG_SIZE < 4) MSG_SIZE = 4;

    try {
      if (DEBUG) System.err.println("PingpongUDP: Creating pingpong object...");
      np = new PingpongUDP(args[1], sending);
      if (DEBUG) System.err.println("PingpongUDP: Calling setup...");
      np.setup();
      np.doIt();
      System.exit(0);

    } catch (Exception e) {
      if (VERBOSE) System.err.println("PingpongUDP.main() got exception: "+e);
      if (VERBOSE) e.printStackTrace();
      System.exit(0);
    }
  }

}
