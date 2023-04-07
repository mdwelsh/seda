/* 
 * Copyright (c) 2001 by Matt Welsh and The Regents of the University of 
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
import seda.util.StatsGatherer;

import java.util.Random;
import java.util.StringTokenizer;

/**
 * This version changes the processing rate 'mu' according to a
 * given schedule.
 */

public class ProcessStageExpCPUSched extends ProcessStage {

  private static final boolean DEBUG = false;

  private double MU_SCHED[];
  private int MSGS_SCHED[];
  private Random rand;

  int num_processed = 0;
  int cur_sched = 0;

  private static final boolean PROFILE = true;
  private StatsGatherer serviceTimeStats, countStats;

  public void init(ConfigDataIF config) throws Exception {
    super.init(config);

    String sched = config.getString("sched");
    if (sched == null) { 
      throw new Exception("Must specify sched");
    }

    int sched_len = 0;
    StringTokenizer st = new StringTokenizer(sched, " :");
    while (st.hasMoreTokens()) {
      String rate = st.nextToken();
      String nummsgs = st.nextToken();
      sched_len++;
    }

    MU_SCHED = new double[sched_len];
    MSGS_SCHED = new int[sched_len];
    sched_len = 0;

    System.err.print(config.getStage().getName()+": Started, sched=");

    st = new StringTokenizer(sched, " :");
    while (st.hasMoreTokens()) {
      int nummsgs = Integer.valueOf(st.nextToken()).intValue();
      double rate = Double.valueOf(st.nextToken()).doubleValue();
      System.err.print(nummsgs+":"+rate+" ");
      MSGS_SCHED[sched_len] = nummsgs;
      MU_SCHED[sched_len] = rate;
      sched_len++;
    }
    System.err.println("");

    System.err.println("ProcessStage: setting mu: "+MU_SCHED[cur_sched]);
    rand = new Random();
    serviceTimeStats = new StatsGatherer("service time", "ST", 1, 0);
    countStats = new StatsGatherer("service count", "SC", 1, 0);
  }

  protected void processMessage(Message msg) {
    if (DEBUG) System.err.println("processMessage: Processing "+msg);

    num_processed++;
    if (num_processed == MSGS_SCHED[cur_sched]) {
      num_processed = 0;
      cur_sched++;
      if (cur_sched == MSGS_SCHED.length) cur_sched = 0;
      System.err.println("ProcessStage: setting mu: "+MU_SCHED[cur_sched]);
    }

    double raw_cpu = -1.0 * Math.log(1 - rand.nextDouble()) / MU_SCHED[cur_sched];
    long cpu_time = Math.max(0L, (long)(raw_cpu * 1.0e3));

    long t1, t2;

    t1 = System.currentTimeMillis();
    int n = 0;
    t2 = System.currentTimeMillis();
    while ((t2 - t1) < cpu_time) {
      n++;
      t2 = System.currentTimeMillis();
    }

    if (PROFILE) {
      serviceTimeStats.add(t2-t1);
      countStats.add(n);
    }

  }
}

