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
import seda.util.MDWUtil;
import seda.util.StatsGatherer;

public class ClientStageMulticlass extends ClientStage 
  implements EventHandlerIF, SimpleP2PConst {

  private static final boolean DEBUG = false;
  private static final boolean VERBOSE = true;

  // Probabilities of each Message belonging to a given class
  private static final double CLASS_PROBS[] = { 0.7, 0.2, 0.1 };
  // Per-class delay bounds in msec; should correspond to controller targets 
  private static final double CLASS_DEADLINES[] = { 5000.0, 1000.0, 200.0 };

  protected StatsGatherer respStats[];
  protected StatsGatherer timeoutStats[];
  protected StatsGatherer continuousRespStats[];
  protected StatsGatherer rejectedRespStats[];

  public void init(ConfigDataIF config) throws Exception {
    super.init(config);
    System.err.println("** ClientStageMulticlass: "+CLASS_PROBS.length+" classes");

    respStats = new StatsGatherer[CLASS_PROBS.length];
    timeoutStats = new StatsGatherer[CLASS_PROBS.length];
    continuousRespStats = new StatsGatherer[CLASS_PROBS.length];
    rejectedRespStats = new StatsGatherer[CLASS_PROBS.length];
    for (int c = 0; c < CLASS_PROBS.length; c++) {
      respStats[c] = new StatsGatherer("Response time (class "+c+")", "RT"+c, BUCKETSIZE, SKIP_SAMPLES);
      timeoutStats[c] = new StatsGatherer("Timeout response time (class "+c+")", "TRT"+c, BUCKETSIZE, SKIP_SAMPLES);
      continuousRespStats[c] = new StatsGatherer("Continuous response time (class "+c+")", "CRT"+c, BUCKETSIZE, SKIP_SAMPLES);
      rejectedRespStats[c] = new StatsGatherer("Rejected RT (class "+c+")", "REJRT"+c, BUCKETSIZE, SKIP_SAMPLES);
    }
  }
  
  class sendThread extends ClientStage.sendThread implements Runnable {

    public void run() {
      while (arrivals.size() > 0) {
	Double arr = (Double)arrivals.remove(0);
	long t1 = System.currentTimeMillis();
	while ((System.currentTimeMillis() - t1) < arr.doubleValue()) {
	  t1 = System.currentTimeMillis();
	}

	double d = rand.nextDouble();
	int theclass = 0;
	while (d > CLASS_PROBS[theclass]) {
	  d -= CLASS_PROBS[theclass];
	  theclass++;
	}

	ClassedMessage msg = new ClassedMessage(cur_seqNum++, 0, theclass, recvSink, compQ);
	msgTbl.put(msg, new MessageTimer());
	msg.send();
      }
    }
  }

  protected void doneWithMsg(Message msg, MessageTimer mt, long cur_time) {
    ClassedMessage cmsg = (ClassedMessage)msg;
    if (cmsg.status == Message.STATUS_OK) {
      total_completed++;
      respStats[cmsg.theclass].add(cur_time - mt.time);
      continuousRespStats[cmsg.theclass].add(cur_time - mt.time);

      if ((cur_time - mt.time) > (CLASS_DEADLINES[cmsg.theclass])) {
	// Delay bound violation
	timeoutStats[cmsg.theclass].add(cur_time - mt.time);
      }

    } else {
      rejectedRespStats[cmsg.theclass].add(cur_time - mt.time);
      total_rejected++;
    }
  }

  protected void resetStats() {
    for (int c = 0; c < CLASS_PROBS.length; c++) {
      respStats[c].reset();
      timeoutStats[c].reset();
      rejectedRespStats[c].reset();
    }
  }

  protected MessageSendEvent createMSE(int num_msgs) {
    return new ClassedMessageSendEvent(num_msgs);
  }

  protected void doReport(long t1, long t2) {
    int completed = 0;
    int timedout = 0;
    int rejected = 0;
    for (int c = 0; c < CLASS_PROBS.length; c++) {
      completed += respStats[c].num;
      timedout += timeoutStats[c].num;
      rejected += rejectedRespStats[c].num;
    }
    int total = completed+rejected;

    System.err.println("\n"+total+" messages in "+(t2-t1)+" msec");
    double rate = (total * 1.0) / ((t2-t1)*1.0e-3);
    System.err.println("Overall rate:\t"+MDWUtil.format(rate)+" msgs/sec");

    double total_frac_rejected = (rejected * 1.0) / (total * 1.0);
    System.err.println(rejected+" rejected, fraction "+total_frac_rejected);

    for (int c = 0; c < CLASS_PROBS.length; c++) {
      int compl = respStats[c].num;
      int tout = timeoutStats[c].num;
      int rej = rejectedRespStats[c].num;
      double frac_rejected = (rej * 1.0) / ((compl+rej) * 1.0);
      double frac_timedout = (tout * 1.0) / (compl * 1.0);

      System.err.println("RT"+c+": avg "+respStats[c].mean()+" max "+respStats[c].max()+" 90th "+respStats[c].percentile(0.9));
      System.err.println("CRT"+c+": avg "+continuousRespStats[c].mean()+" max "+continuousRespStats[c].max()+" 90th "+continuousRespStats[c].percentile(0.9));
      System.err.println("REJRT"+c+": avg "+rejectedRespStats[c].mean()+" max "+rejectedRespStats[c].max()+" 90th "+rejectedRespStats[c].percentile(0.9)+" frac_rejected "+MDWUtil.format(frac_rejected)+" frac_timedout "+MDWUtil.format(frac_timedout)+"\n");
    }
  }

  class ClassedMessageSendEvent extends ClientStage.MessageSendEvent 
    implements QueueElementIF {

    public ClassedMessageSendEvent(int numMsgs) {
      super(numMsgs);
    }

    public Message[] getMessages() {
      ClassedMessage marr[] = new ClassedMessage[numMsgs];
      for (int i = 0; i < marr.length; i++) {

	double d = rand.nextDouble();
	int theclass = 0;
	while (d > CLASS_PROBS[theclass]) {
	  d -= CLASS_PROBS[theclass];
	  theclass++;
	}

	marr[i] = new ClassedMessage(cur_seqNum++, 0, theclass, recvSink, compQ);
	if (cur_seqNum == NUM_MSGS) 
	  System.err.println("ClientStageMultithreaded: SENT LAST MESSAGE ------------------");
      }
      return marr;
    }
  }


}
