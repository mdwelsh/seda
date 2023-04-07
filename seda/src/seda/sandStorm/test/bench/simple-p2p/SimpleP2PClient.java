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

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.QueueIF;
import seda.sandStorm.api.SinkClosedException;
import seda.sandStorm.api.SinkDrainedEvent;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.core.ssTimer;
import seda.sandStorm.lib.aSocket.ATcpClientSocket;
import seda.sandStorm.lib.aSocket.ATcpConnection;
import seda.sandStorm.lib.aSocket.ATcpInPacket;
import seda.util.MDWUtil;
import seda.util.StatsGatherer;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

public class SimpleP2PClient implements SimpleP2PConst {
  private static final boolean DEBUG = false;
  private static final boolean VERBOSE = true;

  private static final boolean CONSTANT_RATE = false;
  private static final int BUCKETSIZE = 1;
  private static final int SKIP_SAMPLES = 0;
  private static final int MIN_DELAY = 50;
  
  protected static String HOSTNAME;
  protected static double RATE;
  protected static int NUM_MSGS;
  protected static StatsGatherer respStats;

  private QueueIF compQ = null;
  private ATcpConnection conn;
  private ATcpClientSocket clisock;
  private int total_rcv = 0;
  private Hashtable msgTbl = new Hashtable();

  private int cur_seqNum = 0;
  private ssTimer timer;
  private long MS_WAIT;
  private int MSGS_PER_SEND;
  private Vector sendEvents;

  public SimpleP2PClient() throws Exception {
    this.compQ = new FiniteQueue();
    this.clisock = new ATcpClientSocket(HOSTNAME, PORT, compQ);
  }

  // Initialize the sending process
  private void startSender() {
    if (CONSTANT_RATE) {
      // Constant delivery rate
      double delay = ((1.0 / RATE) * 1.0e3);
      MS_WAIT = Math.max((long)delay, MIN_DELAY);
      MSGS_PER_SEND = Math.max(1, (int)(MS_WAIT / delay));
      System.err.println("Delay between sends: "+MS_WAIT+", "+MSGS_PER_SEND+" msgs/send");
      timer = new ssTimer();
      timer.registerEvent(MS_WAIT, new MessageSendEvent(MSGS_PER_SEND), compQ);
    } else {
      // Exponential delivery rate
      MS_WAIT = MIN_DELAY;
      sendEvents = new Vector();
      Random rand = new Random();
      int cur_msgs = 0;
      double cur_time = 0.0;
      for (int i = 0; i < NUM_MSGS; i++) {
	double arr_time = (-1.0 * Math.log(1 - rand.nextDouble()) / (RATE * 1.0));
	arr_time *= 1.0e3;

	if (cur_time + arr_time > MIN_DELAY) {
	  sendEvents.addElement(new MessageSendEvent(cur_msgs));
	  // Skip over empty quanta
	  int num_to_skip = ((int)((cur_time + arr_time) / MIN_DELAY)) - 1;
	  for (int j = 0; j < num_to_skip; j++) {
	    sendEvents.addElement(new MessageSendEvent(0));
	  }
	  cur_msgs = 1;
	  cur_time = (cur_time + arr_time) - (MIN_DELAY * (num_to_skip+1));

	} else {
	  cur_msgs++;
	  cur_time += arr_time;
	}
      }
      sendEvents.addElement(new MessageSendEvent(cur_msgs));
      timer = new ssTimer();
      timer.registerEvent(MS_WAIT, (MessageSendEvent)(sendEvents.remove(0)), compQ);

    }
  }

  private long send_last;

  // Call the sending process
  private void doSender() {
    if (CONSTANT_RATE) {
      timer.registerEvent(MS_WAIT, new MessageSendEvent(MSGS_PER_SEND), compQ);
    } else {
      if (sendEvents.size() > 0) {
	timer.registerEvent(MS_WAIT, (MessageSendEvent)(sendEvents.remove(0)), 
	    compQ);
      }
    }
  }

  public void doIt() throws SinkClosedException, IOException {
    int i;
    int n = 0;

    if (DEBUG) System.err.println("SimpleP2PClient: doIt called");

    QueueElementIF fetched[];

    /* Wait for connection */
    if (DEBUG) System.err.println("SimpleP2PClient: Waiting for connection to complete");
    while (true) {
      while ((fetched = compQ.blocking_dequeue_all(0)) == null) ;
      if (fetched.length != 1) throw new IOException("Got more than one event on initial fetch?");
      if (!(fetched[0] instanceof ATcpConnection)) continue;
      break;
    }
    conn = (ATcpConnection)fetched[0];
    System.err.println("Got connection from "+conn.getAddress()+":"+conn.getPort());
    conn.startReader(compQ);
    MessageReader msr = new MessageReader(compQ);

    startSender();
    
    while (true) {

      if (DEBUG) System.err.println("SimpleP2PClient: Waiting for dequeue...");

      while ((fetched = compQ.blocking_dequeue_all(0)) == null) ;

      for (i = 0; i < fetched.length; i++) {
	if (DEBUG) System.err.println("SimpleP2PClient: Got QEL: "+fetched[i]);

	if (fetched[i] instanceof MessageSendEvent) {
	  // Send messages (enqueued by the sending process)
	  MessageSendEvent mse = (MessageSendEvent)fetched[i];
	  Message marr[] = mse.getMessages();
	  for (int j = 0; j < marr.length; j++) {
	    Message msg = marr[j];
	    msgTbl.put(msg, new MessageTimer());
	    msg.send();
	  }
	  doSender();

	} else if (fetched[i] instanceof ATcpInPacket) {
	  // Process an incoming packet

          ATcpInPacket pkt = (ATcpInPacket)fetched[i];
	  msr.parse(pkt);

	} else if (fetched[i] instanceof Message) {
	  // Process an incoming message
	  Message msg = (Message)fetched[i];
	  MessageTimer mt = (MessageTimer)msgTbl.remove(msg);
	  if (mt == null) {
	    throw new RuntimeException("FATAL: Message "+msg+" not found in msgTbl");
	  }
	  long cur = System.currentTimeMillis();

	  // Do stats
	  respStats.add(cur - mt.time);

	  // Check if finished
	  if (++total_rcv == NUM_MSGS) return;

	} else if (fetched[i] instanceof SinkDrainedEvent) {
	  if (DEBUG) System.err.println("Got SinkDrainedEvent!");
	} else {
	  throw new IOException("Got unknown event: "+fetched[i].toString());
	}
      }
    }

  }

  class MessageSendEvent implements QueueElementIF {
    int numMsgs;

    MessageSendEvent(int numMsgs) {
      this.numMsgs = numMsgs;
    }

    Message[] getMessages() {
      Message marr[] = new Message[numMsgs];
      for (int i = 0; i < marr.length; i++) {
	marr[i] = new Message(cur_seqNum++, 0, conn);
      }
      return marr;
    }
  }

  class MessageTimer {
    long time;

    MessageTimer() {
      this.time = System.currentTimeMillis();
    }
  }

  private static void usage() {
    System.err.println("usage: SimpleP2PClient <remote_hostname> <rate> <nummsgs>");
    System.exit(1);
  }

  public static void main(String args[]) {
    SimpleP2PClient client;
    long t1, t2;

    try {
      if (args.length != 3) usage();

      HOSTNAME = args[0];
      RATE = Double.valueOf(args[1]).doubleValue();
      NUM_MSGS = Integer.valueOf(args[2]).intValue();

      respStats = new StatsGatherer("Response time", "RT", BUCKETSIZE, SKIP_SAMPLES);

      System.err.println("SimpleP2PClient: hostname="+HOSTNAME+", rate="+RATE);
      
      client = new SimpleP2PClient();

      t1 = System.currentTimeMillis();
      client.doIt();
      t2 = System.currentTimeMillis();

      System.err.println(NUM_MSGS+" messages in "+(t2-t1)+" msec");
      double rate = (NUM_MSGS * 1.0) / ((t2-t1)*1.0e-3);
      System.err.println("Overall rate:\t"+MDWUtil.format(rate)+" msgs/sec");
      respStats.dumpHistogram();

      System.exit(0);

    } catch (Exception e) {
      if (VERBOSE) System.err.println("main() got exception: "+e);
      if (VERBOSE) e.printStackTrace();
    }
  }

}
