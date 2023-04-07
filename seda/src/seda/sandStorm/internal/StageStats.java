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

package seda.sandStorm.internal;

import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.ProfilableIF;
import seda.sandStorm.api.internal.StageStatsIF;
import seda.sandStorm.api.internal.StageWrapperIF;
import seda.sandStorm.main.Sandstorm;

/**
 * This class provides controllers with a view of statistics gathered
 * by the stage internally during execution.
 * 
 * @author   Matt Welsh
 */

public class StageStats implements StageStatsIF {
  private static final boolean DEBUG = false;
  private static final boolean PROFILE = false;

  private static final double SMOOTH_ALPHA = 0.7;
  private static final int ESTIMATION_SIZE = 100;
  private static final long ESTIMATION_TIME = 1000;

  /* A handle to the stage. */
  StageWrapperIF stage;

  /* Average service rate of events. */
  private double serviceRate;

  /* 90th percentile response time of the stage. */
  private double rt90thPercentile;

  private boolean first = true;
  private long lastTime;
  private int count;
  private long totalServiceTime, totalEvents, cumulativeEvents;

  public StageStats(StageWrapperIF stage) {
    this.stage = stage;
    reset();
  }

  /** Reset all statistics. */
  public void reset() {
    serviceRate = 0.0;
    count = 0;
    lastTime = System.currentTimeMillis();
    totalEvents = totalServiceTime = cumulativeEvents = 0;
  }

  /** Return a moving average of the service rate. */
  public synchronized double getServiceRate() {
    return serviceRate;
  }

  /** Get total number of processed events. */
  public synchronized long getTotalEvents() {
    return this.cumulativeEvents;
  }

  /** 
   * Record the service time for numEvents taking 'time' msec to
   * be processed.
   */
  public synchronized void recordServiceRate(int numEvents, long time) {

    // Only possible to add ourselves to the profile after we start running
    if (PROFILE && first) {
      first = false;
      ManagerIF mgr = Sandstorm.getSandstorm().getManager();
      if (mgr.getProfiler() != null) {
	mgr.getProfiler().add("StageStats serviceRate <"+stage.getStage().getName()+">",
	    new ProfilableIF() {
	    public int profileSize() {
	    return (int)serviceRate;
	    }});
      }
    }

    totalEvents += numEvents;
    cumulativeEvents += numEvents;
    totalServiceTime += time;

    count++;
    long curTime = System.currentTimeMillis();

    if ((count == ESTIMATION_SIZE) ||
	(curTime - lastTime >= ESTIMATION_TIME)) {
      if (totalServiceTime == 0) totalServiceTime = 1;
      double rate = totalEvents / (totalServiceTime * 1.0e-3);
      serviceRate = (rate * SMOOTH_ALPHA) + (serviceRate * (1.0 - SMOOTH_ALPHA));
      if (DEBUG) System.err.println("Stats <"+stage.getStage().getName()+">: numEvents="+totalEvents+" time="+totalServiceTime+", rate="+serviceRate);
      count = 0;
      lastTime = curTime;
      totalEvents = totalServiceTime = 0;
    }
  }

  /** Record 90th percentile response time in msec. */
  public synchronized void record90thRT(double rt_sample) {
    this.rt90thPercentile = rt_sample;
  }

  /** Get 90th percentile response time in msec. */
  public synchronized double get90thRT() {
    return this.rt90thPercentile;
  }



}

