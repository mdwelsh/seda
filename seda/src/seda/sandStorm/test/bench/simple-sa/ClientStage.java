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

import seda.sandStorm.api.ConfigDataIF;
import seda.sandStorm.api.EventHandlerIF;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.core.ssTimer;
import seda.util.MDWUtil;
import seda.util.StatsGatherer;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

public class ClientStage implements EventHandlerIF, SimpleP2PConst {
  private static final boolean DEBUG = false;
  private static final boolean VERBOSE = true;

  protected static final double WARMUP_FRAC = 0.1;
  protected static final boolean CONSTANT_RATE = true;
  protected static final boolean SPIN_SEND_THREAD = false;
  protected static final int BUCKETSIZE = 1;
  protected static final int SKIP_SAMPLES = 0;
  protected static final int MIN_DELAY = 10;

  // If -1, only report at end
  protected static final int REPORT_SAMPLES = 200;
  
  protected static double RATE;
  protected static int NUM_MSGS;
  protected static int WARMUP_MSGS;
  protected static StatsGatherer respStats, rejectedRespStats, 
    continuousRespStats, injectTimeStats, interArrivalTimeStats;
  protected static Random rand = new Random();

  protected SinkIF compQ = null;
  protected SinkIF recvSink = null;
  protected int total_rcv = 0;
  protected int total_completed = 0, total_rejected = 0;
  protected Hashtable msgTbl = new Hashtable();

  protected int cur_seqNum = 0;
  protected ssTimer timer;
  protected long MS_WAIT;
  protected int MSGS_PER_SEND;
  protected Vector sendEvents, arrivals;
  protected long t_start, t_last, t_end;

  public void init(ConfigDataIF config) throws Exception {
    compQ = config.getStage().getSink();

    String nextHandler = config.getString("recv_handler");
    if (nextHandler == null) 
      throw new Exception("Warning: RecvStage: Must specify recv_handler");
    recvSink = config.getManager().getStage(nextHandler).getSink();

    RATE = config.getDouble("rate");
    if (RATE == -1.0) 
      throw new Exception("Warning: RecvStage: Must specify rate");

    NUM_MSGS = config.getInt("num_msgs");
    if (NUM_MSGS == -1) 
      throw new Exception("Warning: RecvStage: Must specify num_msgs");

    respStats = new StatsGatherer("Response time", "RT", BUCKETSIZE, SKIP_SAMPLES);
    continuousRespStats = new StatsGatherer("Continuous response time", "CRT", BUCKETSIZE, SKIP_SAMPLES);
    rejectedRespStats = new StatsGatherer("Rejected RT", "REJRT", BUCKETSIZE, SKIP_SAMPLES);
    injectTimeStats = new StatsGatherer("Inject time", "INJRT", BUCKETSIZE, SKIP_SAMPLES);
    interArrivalTimeStats = new StatsGatherer("Inter arrival time", "IAT", BUCKETSIZE, SKIP_SAMPLES);
    startSender();
    t_start = t_last = System.currentTimeMillis();
    WARMUP_MSGS = (int)(WARMUP_FRAC * NUM_MSGS);

    System.err.println("ClientStage: Started, rate="+RATE+", num_msgs="+NUM_MSGS);
  }

  public void destroy() {
  }

  // Initialize the sending process
  protected void startSender() {

    if (CONSTANT_RATE) {
      // Constant delivery rate
      double delay = ((1.0 / RATE) * 1.0e3);
      MS_WAIT = Math.max((long)delay, MIN_DELAY);
      MSGS_PER_SEND = Math.max(1, (int)(MS_WAIT / delay));
      System.err.println("Delay between sends: "+MS_WAIT+", "+MSGS_PER_SEND+" msgs/send");
      timer = new ssTimer();
      timer.registerEvent(MS_WAIT, createMSE(MSGS_PER_SEND), compQ);
    } else {
      // Exponential delivery rate
      MS_WAIT = MIN_DELAY;
      sendEvents = new Vector();
      int cur_msgs = 0;
      double cur_time = 0.0;

      arrivals = new Vector();

      for (int i = 0; i < NUM_MSGS; i++) {
	double arr_time = (-1.0 * Math.log(1 - rand.nextDouble()) / (RATE * 1.0));
	arrivals.addElement(new Double(arr_time));

	arr_time *= 1.0e3;
	interArrivalTimeStats.add((long)arr_time);

	if (cur_time + arr_time > MIN_DELAY) {
	  sendEvents.addElement(createMSE(cur_msgs));
	  // Skip over empty quanta
	  int num_to_skip = ((int)((cur_time + arr_time) / MIN_DELAY)) - 1;
	  for (int j = 0; j < num_to_skip; j++) {
	    sendEvents.addElement(createMSE(0));
	  }
	  cur_msgs = 1;
	  cur_time = (cur_time + arr_time) - (MIN_DELAY * (num_to_skip+1));

	} else {
	  cur_msgs++;
	  cur_time += arr_time;
	}
      }
      sendEvents.addElement(createMSE(cur_msgs));
      timer = new ssTimer();

      if (SPIN_SEND_THREAD) {
	Thread st = new Thread(new sendThread());
	st.start();
      } else {
  	timer.registerEvent(MS_WAIT, (MessageSendEvent)(sendEvents.remove(0)), compQ);
      }

      interArrivalTimeStats.dumpHistogram();
    }

  }

  // Call the sending process
  protected void doSender() {
    if (CONSTANT_RATE) {
      timer.registerEvent(MS_WAIT, createMSE(MSGS_PER_SEND), compQ);
    } else {
      if (sendEvents.size() > 0) {
	timer.registerEvent(MS_WAIT, (MessageSendEvent)(sendEvents.remove(0)), 
	    compQ);
      }
    }
  }

  protected void doneWithMsg(Message msg, MessageTimer mt, long cur_time) {
    if (msg.status == Message.STATUS_OK) {
      total_completed++;
      respStats.add(cur_time - mt.time);
      continuousRespStats.add(cur_time - mt.time);
    } else {
      rejectedRespStats.add(cur_time - mt.time);
      total_rejected++;
    }
  }

  protected MessageSendEvent createMSE(int num_msgs) {
    return new MessageSendEvent(num_msgs);
  }

  protected void resetStats() {
    respStats.reset();
    rejectedRespStats.reset();
  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

  class sendThread implements Runnable {

    public void run() {
      while (arrivals.size() > 0) {
	Double arr = (Double)arrivals.remove(0);
	long t1 = System.currentTimeMillis();
	while ((System.currentTimeMillis() - t1) < arr.doubleValue()) {
	  t1 = System.currentTimeMillis();
	}
	Message msg = new Message(cur_seqNum++, 0, recvSink, compQ);
	msgTbl.put(msg, new MessageTimer());
	msg.send();
      }
    }
  }


  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("ClientStage: Got QEL: "+item);

    if (item instanceof MessageSendEvent) {
      // Send messages (enqueued by the sending process)
      MessageSendEvent mse = (MessageSendEvent)item;
      Message marr[] = mse.getMessages();
      for (int j = 0; j < marr.length; j++) {
	Message msg = marr[j];
	msgTbl.put(msg, new MessageTimer());
	msg.send();
      }
      doSender();

    } else if (item instanceof Message) {
      // Process an incoming message
      Message msg = (Message)item;
      MessageTimer mt = (MessageTimer)msgTbl.remove(msg);
      if (mt == null) {
	throw new RuntimeException("FATAL: Message "+msg+" not found in msgTbl");
      }
      long cur = System.currentTimeMillis();

      if (total_rcv == 0) {
	System.err.println("ClientStage: Starting warmup phase");
      }
      if (total_rcv == WARMUP_MSGS) {
	System.err.println("ClientStage: Ending warmup phase");
	t_start = t_last = System.currentTimeMillis();

	// Clear all StatsGatherers
	Enumeration e = StatsGatherer.lookupAll();
	while (e.hasMoreElements()) {
	  StatsGatherer sg = (StatsGatherer)e.nextElement();
	  sg.reset();
	}

      }
      if (++total_rcv >= WARMUP_MSGS) {
	// Do stats
	doneWithMsg(msg, mt, cur);
      }

      if ((REPORT_SAMPLES != -1) &&
	  ((total_rcv % REPORT_SAMPLES) == 0) &&
	  (total_rcv >= WARMUP_MSGS)) { 
	doReport(t_last, System.currentTimeMillis());
	resetStats();
	t_last = System.currentTimeMillis();
      }

      // Check if finished
      if (total_rcv == NUM_MSGS) {
	System.err.println("\n------------------------------------------");
	if (REPORT_SAMPLES != -1) {
	  doReport(t_last, System.currentTimeMillis());
	} else {
	  t_end = System.currentTimeMillis();
	  doReport(t_start, t_end);
	}
	System.err.println("------------------------------------------");

	Enumeration e = StatsGatherer.lookupAll();
	while (e.hasMoreElements()) {
	  StatsGatherer sg = (StatsGatherer)e.nextElement();
	  sg.dumpHistogram();
	}
	System.exit(0);
      }

    } else {
      System.err.println("ClientStage: Got unknown event: "+item);
    }
  }

  protected void doReport(long t1, long t2) {
    int completed = respStats.num;
    int rejected = rejectedRespStats.num;
    int total = completed+rejected;

    System.err.println("\n"+total+" messages in "+(t2-t1)+" msec");
    double rate = (total * 1.0) / ((t2-t1)*1.0e-3);
    System.err.println("Overall rate:\t"+MDWUtil.format(rate)+" msgs/sec");

    double frac_rejected = (rejected * 1.0) / (total * 1.0);
    System.err.println(rejected+" rejected, fraction "+frac_rejected);

    System.err.println("RT: avg "+respStats.mean()+" max "+respStats.max()+" 90th "+respStats.percentile(0.9));
    System.err.println("CRT: avg "+continuousRespStats.mean()+" max "+continuousRespStats.max()+" 90th "+continuousRespStats.percentile(0.9));
    System.err.println("REJRT: avg "+rejectedRespStats.mean()+" max "+rejectedRespStats.max()+" 90th "+rejectedRespStats.percentile(0.9)+"\n");

  }

  class MessageSendEvent implements QueueElementIF {
    int numMsgs;

    public MessageSendEvent(int numMsgs) {
      this.numMsgs = numMsgs;
    }

    public Message[] getMessages() {
      Message marr[] = new Message[numMsgs];
      for (int i = 0; i < marr.length; i++) {
	marr[i] = new Message(cur_seqNum++, 0, recvSink, compQ);
	if (cur_seqNum == NUM_MSGS) System.err.println("ClientStage: SENT LAST MESSAGE ------------------");
      }
      return marr;
    }
  }

  static long last_inject_time;
  static boolean first_mt = true;
  class MessageTimer {
    long time;

    MessageTimer() {
      if (first_mt) {
	last_inject_time = t_start;
	first_mt = false;
      }
      this.time = System.currentTimeMillis();
      injectTimeStats.add(this.time - last_inject_time);
      last_inject_time = this.time;

    }
  }

}
