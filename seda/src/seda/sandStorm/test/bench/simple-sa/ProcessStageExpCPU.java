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
import seda.util.MDWUtil;
import seda.util.StatsGatherer;

import java.util.Random;

public class ProcessStageExpCPU extends ProcessStage {

  private static final boolean DEBUG = false;

  private double MU;
  private Random rand;
  private int num_processed = 0;

  private static final boolean PROFILE = true;
  private StatsGatherer serviceTimeStats, targetServiceTimeStats, countStats;

  public void init(ConfigDataIF config) throws Exception {
    super.init(config);

    MU = config.getDouble("mu");
    if (MU == -1.0) { 
      throw new Exception("Must specify mu");
    }
    rand = new Random();

    serviceTimeStats = new StatsGatherer("service time", "ST", 1, 0);
    targetServiceTimeStats = new StatsGatherer("target service time", "TST", 1, 0);
    countStats = new StatsGatherer("service count", "SC", 1, 0);
    System.err.println(config.getStage().getName()+": Started, mu="+MU);

  }

  protected void processMessage(Message msg) {
    if (DEBUG) System.err.println("processMessage: Processing "+msg);

    num_processed++;

    double raw_cpu = -1.0 * Math.log(1 - rand.nextDouble()) / this.MU;
    long cpu_time = Math.max(0L, (long)(raw_cpu * 1.0e3));
    cpu_time *= 1.0e3; // Scale to usec

    long t1, t2;

    t1 = MDWUtil.currentTimeUsec();
    int n = 0;
    t2 = MDWUtil.currentTimeUsec();
    while ((t2 - t1) < cpu_time) {
      n++;
      t2 = MDWUtil.currentTimeUsec();
    }

    if (PROFILE) {
      targetServiceTimeStats.add(cpu_time);
      serviceTimeStats.add(t2-t1);
      countStats.add(n);
    }


  }
}

