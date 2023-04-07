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

/* This is a server which accepts multiple connections from clients running
 * the 'Bandwidth' or 'MultiClient' clients and reads packets from them, 
 * sending bursts of a given size. The idea is to demonstrate the 
 * performance of the aSocket layer with multiple client machines loading it. 
 */

import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.lib.aSocket.ATcpConnection;
import seda.sandStorm.lib.aSocket.ATcpInPacket;
import seda.sandStorm.lib.aSocket.ATcpServerSocket;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Hashtable;

public class MultiBandwidth {
  
  private static ATcpServerSocket servsock;
  private static QueueIF comp_q = null;
  private static Hashtable conn_table;
  private static int num_connections = 0;
  private static int num_active_connections = 0;

  private static final boolean DEBUG = false;
  private static final boolean VERBOSE = true;
  private static boolean BLOCKING_DEQUEUE = true;
  private static boolean FAIRNESS_REPORT = true;

  private static final int PORTNUM = 5721;

  private static int SEND_MESSAGE_SIZE;
  private static int SEND_BURST_SIZE;
  private static int RECV_MESSAGE_SIZE;
  private static int RECV_BURST_SIZE;


  public MultiBandwidth() {
    conn_table = new Hashtable();
  }

  public void setup() throws IOException, UnknownHostException {

    comp_q = new FiniteQueue();
    servsock = new ATcpServerSocket(PORTNUM, comp_q);
  }

  class ConnState {
    SinkIF sink;
    int num_messages;
    int cur_offset, cur_length_target;
    int burstsSent;
    boolean firstRead;

    ConnState(SinkIF sink) {
      this.sink = sink;
      this.num_messages = 0;
      this.cur_offset = 0;
      this.cur_length_target = RECV_MESSAGE_SIZE*RECV_BURST_SIZE;
      this.firstRead = true;
    }

    boolean readDone(int size) {
      if (firstRead) {
	firstRead = false;
	num_active_connections++;
      }

      if (DEBUG) System.err.println("Adding "+size+" bytes to "+this.cur_offset);
      this.cur_offset += size;
      if (DEBUG) System.err.println("Total now "+this.cur_offset+", target "+this.cur_length_target);

      if (this.cur_offset == this.cur_length_target) {
	if (DEBUG) System.err.println("Reached cur_length_target");
        num_messages++;
        return true;
      } else {
        return false;
      }
    }

    void reset() {
      this.cur_offset = 0;
      this.cur_length_target = RECV_MESSAGE_SIZE*RECV_BURST_SIZE;
    }

  }

  public void doIt() throws SinkClosedException, IOException {
    int i;
    int n = 0;

    int num_received = 0;
    byte sendmsg[] = new byte[SEND_MESSAGE_SIZE];
    BufferElement sendbuf = new BufferElement(sendmsg);

    QueueElementIF fetched[];
    long before, after;
    long total_bytes = 0;

    before = System.currentTimeMillis();

    while (true) {
      
      if (DEBUG) System.err.println("Waiting for dequeue...");

      if (BLOCKING_DEQUEUE) {
        while ((fetched = comp_q.blocking_dequeue_all(-1)) == null) ;
      } else {
        while ((fetched = comp_q.dequeue_all()) == null) ;
      }

      //System.err.println("FETCHED "+fetched.length+" NEW EVENTS");

      for (i = 0; i < fetched.length; i++) {
	
	if (fetched[i] instanceof ATcpConnection) {
	  System.err.println("Got connection "+num_connections);
	  ATcpConnection conn = (ATcpConnection)fetched[i];
	  num_connections++;

	  conn.startReader(comp_q);
	  ConnState cs = new ConnState(conn);
	  conn_table.put(conn, cs);

        } else if (fetched[i] instanceof ATcpInPacket) {
          ATcpInPacket pkt = (ATcpInPacket)fetched[i];
	  ATcpConnection conn = pkt.getConnection();

          //System.err.println("Got packet: "+pkt.size()+" from conn "+conn);
	  total_bytes += pkt.size(); 
	  n++;
	  //if (n % (num_connections * 100) == 0) {
	  after = System.currentTimeMillis();
	  if ((after - before) >= 5000) {
	    printTime(before, after, total_bytes);
	    before = System.currentTimeMillis();
	    total_bytes = 0;
	  }

	  ConnState cs = (ConnState)conn_table.get(conn);
	  if (cs.readDone(pkt.size())) {
	    if (DEBUG) System.err.println("Sending burst");

	    for (int j = 0; j < SEND_BURST_SIZE; j++) {
              if (!cs.sink.enqueue_lossy(sendbuf)) {
		System.err.println("WARNING: Could not enqueue message onto sink "+cs.sink);
	      }
	      cs.burstsSent++;
            }
	    cs.reset();
	  }

	} else if (fetched[i] instanceof SinkDrainedEvent) {
	  if (DEBUG) System.err.println("Got SinkDrainedEvent!");
	} else if (fetched[i] instanceof SinkClosedEvent) {
	  if (DEBUG) System.err.println("Got SinkClosedEvent!");
	  System.err.println("Closed connection "+num_connections);
	  SinkClosedEvent sce = (SinkClosedEvent)fetched[i];
	  conn_table.remove(sce.sink);
	  num_connections--;
	  num_active_connections--;
	} else {
	  System.err.println("Sender got unknown comp_q event: "+fetched[i].toString());
	}
      }
    }

  }

  private static void printTime(long t1, long t2, long msg_size) {
    long diff = t2-t1;
    double mbits = (msg_size * 8.0)/(1024*1024);
    double mbps = mbits / (double)(diff * 1.0e-3);

    System.err.println("Completed: "+msg_size + " bytes in " + diff + " msec");
    System.err.println("\t"+mbps+" mbps bandwidth");
    System.err.println("\t"+num_connections+" connections");
    System.err.println("\t"+num_active_connections+" active connections");

    // Compute fairness
    if (FAIRNESS_REPORT) {
      int totalSent = 0, n = 0;
      int numBad = 0;
      Enumeration e = conn_table.elements();
      while (e.hasMoreElements()) {
	ConnState cs = (ConnState)e.nextElement();
	totalSent += cs.burstsSent;
	n++;
	if (cs.burstsSent == 0) numBad++;
      }
      double averageSent = totalSent / n;
      double t = 0;
      e = conn_table.elements();
      while (e.hasMoreElements()) {
	ConnState cs = (ConnState)e.nextElement();
	t += (cs.burstsSent - averageSent) * (cs.burstsSent - averageSent);
	cs.burstsSent = 0;
      }
      double stddev = Math.sqrt(t / (n - 1));
      System.err.println("\t"+averageSent+" avg bursts/conn, stddev "+stddev);
      System.err.println("\t"+numBad+" connections with no bursts");
    }
  }

  private static void usage() {
    System.err.println("usage: MultiBandwidth <send-msg-size> <send-burst-size> <recv-msg-size> <recv-burst-size>");
    System.exit(1);
  }

  public static void main(String args[]) {
    MultiBandwidth np;
    boolean sending = false;

    if (args.length != 4) usage();

    SEND_MESSAGE_SIZE = Integer.decode(args[0]).intValue();
    SEND_BURST_SIZE = Integer.decode(args[1]).intValue();
    RECV_MESSAGE_SIZE = Integer.decode(args[2]).intValue();
    RECV_BURST_SIZE = Integer.decode(args[3]).intValue();

    try {
      System.err.println("MultiBandwidth: send message size="+SEND_MESSAGE_SIZE+", send burst size="+SEND_BURST_SIZE+", recv message size="+RECV_MESSAGE_SIZE+", recv burst size="+RECV_BURST_SIZE+", rx block="+BLOCKING_DEQUEUE);

      np = new MultiBandwidth();
      np.setup();
      np.doIt();

    } catch (Exception e) {
      System.err.println("MultiBandwidth.main() got exception: "+e);
      e.printStackTrace();
    }
  }

}
