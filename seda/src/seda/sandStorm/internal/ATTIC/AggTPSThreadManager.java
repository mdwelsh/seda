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

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.api.SourceIF;
import seda.sandStorm.api.internal.StageWrapperIF;
import seda.sandStorm.api.internal.ThreadManagerIF;
import seda.sandStorm.main.SandstormConfig;

import java.util.Enumeration;
import java.util.Vector;

/**
 * AggTPSThreadManager is a refinement of the TPSTM; it attempts to
 * schedule stages to improve aggregation. The basic algorithm is to
 * maintain a tunable "aggregation target", the minimum queue size
 * threshold which triggers the execution of a stage's handler. This
 * aggregation target is increased when more than 1 stage can meet
 * the target, and reduced when no stages can meet it. A target of 1
 * is equivalent to the TPSTM algorithm.
 * 
 * @author   Matt Welsh
 */
class AggTPSThreadManager implements ThreadManagerIF, sandStormConst {

  private static final boolean DEBUG = true;
  private static final boolean DEBUG_VERBOSE = false;

  private static final int INITIAL_THREADPOOL_SIZE = 1;
  private int maxAggregation;

  private Vector stages;
  private Vector threadpools;
  private ThreadGroup tg;
  private Thread governor;
  private boolean useGovernor;
  private int governorDelay, governorMaxThreads, governorThreshold;

  // Number of events we would like to wait for before scheduling stage
  private int aggregationTarget = 1;
  private Object lock;

  AggTPSThreadManager(SandstormConfig config) {
    this.useGovernor = config.getBoolean("global.AggTPSTM.governor.enable");
    this.governorDelay = config.getInt("global.AggTPSTM.governor.delay");
    this.governorMaxThreads = config.getInt("global.AggTPSTM.governor.maxThreads");
    this.governorThreshold = config.getInt("global.AggTPSTM.governor.threshold");

    maxAggregation = config.getInt("global.maxBatch");

    stages = new Vector(1);
    tg = new ThreadGroup("AggTPSThreadManager");
    lock = new Object();
  }

  /**
   * Register a stage with this thread manager.
   */
  public void register(StageWrapperIF stage) {

    if (useGovernor && (governor == null)) {
      System.err.println("AggTPSThreadManager: Starting thread governor");
      governor = new Thread(tg, new governorThread(), "AggTPSTM Governor");
      governor.start();
    }

    System.err.println("AggTPSThreadManager: Starting thread pool for "+stage+", maxAggregation "+maxAggregation);
    stageInfo si = new stageInfo(stage);
    stages.addElement(si);
    si.start();
  }

  /**
   * Deregister a stage with this thread manager.
   */
  public void deregister(StageWrapperIF stage) {
    System.err.println("AggTPSThreadManager: Deregistering stage "+stage);
    Enumeration e = stages.elements();
    while (e.hasMoreElements()) {
      stageInfo stageinfo = (stageInfo)e.nextElement();
      if (stageinfo.stage == stage) {
	stageinfo.stop();
      }
    }
    if (!stages.removeElement(stage)) throw new IllegalArgumentException("Stage "+stage+" not registered with this TM");
  }

  /**
   * Stop the thread manager and all threads managed by it.
   */
  public void deregisterAll() {
    Enumeration e = stages.elements();
    while (e.hasMoreElements()) {
      stageInfo stageinfo = (stageInfo)e.nextElement();
      deregister(stageinfo.stage);
    }
  }

  /**
   * Stop the thread manager and all threads managed by it.
   */
  public void stop() {
    System.err.println("AggTPSThreadManager: Stopping "+threadpools.size()+" threadpools");
    tg.stop();
  }

  /**
   * Internal class representing state for a given stage.
   */
  class stageInfo {
    StageWrapperIF stage;
    threadPool tp;

    stageInfo(StageWrapperIF stage) {
      this.stage = stage;
      // Create a threadPool for each stage
      tp = new threadPool(stage, stage.getSource());
    }

    void start() {
      tp.start();
    }

    void stop() {
      tp.stop();
    }
  }

  /**
   * Internal class representing a single AggTPSTM-managed thread.
   */
  class appThread implements Runnable {

    private StageWrapperIF wrapper;
    private SourceIF source;
    private String name;
    private threadPool mytp;

    appThread(StageWrapperIF wrapper, SourceIF source, String name, threadPool tp) {
      this.wrapper = wrapper;
      this.source = source;
      this.name = name;
      this.mytp = tp;
    }

    public void run() {
      if (DEBUG) System.err.println(name+": starting, source is "+source);
      int aTarget;
      boolean needToBlock = false;

      while (true) {

	try {

	  synchronized (lock) {
	    aTarget = aggregationTarget;
	  }

	  if (aTarget > 1) {

	    // First check if my queue has enough elements
	    if (source.size() >= aTarget) {
	      if (DEBUG_VERBOSE) System.err.println(name+": "+source.size()+" elements in queue, dispatching");
	      QueueElementIF fetched[];
	      if (maxAggregation == -1) {
		fetched = source.dequeue_all();
	      } else {
		fetched = source.dequeue(maxAggregation);
	      }
	      wrapper.getEventHandler().handleEvents(fetched);
	      needToBlock = false;
	    } else {
	      needToBlock = true;
	    }

	    // Now check other stages
       	    int numActive = 0;

	    while (numActive == 0) {

	      // Is any other stage ready to run?
	      for (int i = 0; i < stages.size(); i++) {
		stageInfo si = (stageInfo)stages.elementAt(i);
		if (si.tp.source.size() >= aTarget) {
		  // Wake it up
		  synchronized (si.tp) {
		    si.tp.notifyAll();
		  }
		  numActive++;
		}
	      }
	    }

	    if (numActive == 0) {
	      // Reduce aggregationTarget
	      synchronized (lock) {
		aggregationTarget /= 2;
		if (DEBUG) System.err.println("aggTPS: numActive is 0, decreasing aggregationTarget to "+aggregationTarget);

		if (aggregationTarget == 1) {
		  // Wake up every pool
		  for (int i = 0; i < stages.size(); i++) {
		    stageInfo si = (stageInfo)stages.elementAt(i);
		    // Wake it up
		    synchronized (si.tp) {
		      si.tp.notifyAll();
		    }
		  }
		}
	      }
	    } else if (numActive > 1) {
	      // Increase aggregationTarget
	      synchronized (lock) {
		aggregationTarget *= 2;
		if (DEBUG) System.err.println("aggTPS: numActive is "+numActive+", increasing aggregation target to "+aggregationTarget);
	      }
	    }

	    if (needToBlock) {
	      // Wait for another thread to signal
	      synchronized (mytp) {
		try {
		  mytp.wait();
		} catch (InterruptedException ie) {
		  // Ignore
		}
	      }
	    }

	  } else {

	    // If aggregationTarget is 1, all we can do is block
	    if (DEBUG_VERBOSE) System.err.println(name+": Blocking dequeue");
	    QueueElementIF fetched[];
	    if (maxAggregation == -1) {
	      fetched = source.blocking_dequeue_all(-1);
	    } else {
	      fetched = source.blocking_dequeue(-1, maxAggregation);
	    }
	    if (DEBUG_VERBOSE) System.err.println(name+": Got "+fetched.length+" elements");
	    wrapper.getEventHandler().handleEvents(fetched);
	  }

	} catch (Exception e) {
	  System.err.println("AggTPSThreadManager: appThread ["+name+"] got exception "+e);
	  e.printStackTrace();
	}
      }
    }
  }

  class threadPool {
    String stagename;
    StageWrapperIF wrapper;
    SourceIF source;
    private Vector threads;

    threadPool(StageWrapperIF wrapper, SourceIF source) {
      this.wrapper = wrapper;
      this.source = source;
      this.stagename = wrapper.getStage().getName();
      threads = new Vector(1);
      addThreads(1, false);
    }

    void addThreads(int num, boolean start) {
      for (int i = 0; i < num; i++) {
	String name = "AggTPSTM-"+numThreads()+" <"+stagename+">";
	Thread t = new Thread(tg, new appThread(wrapper, source, name, this), name);
	threads.addElement(t);
	if (start) t.start();
      }
    }

    int numThreads() {
      return threads.size();
    }

    void start() {
      System.err.println("  <"+stagename+"> pool: Starting "+numThreads()+" threads");
      for (int i = 0; i < threads.size(); i++) {
	Thread t = (Thread)threads.elementAt(i);
	t.start();
      }
    }

    void stop() {
      System.err.println("  <"+stagename+"> pool: Stopping "+numThreads()+" threads");
      for (int i = 0; i < threads.size(); i++) {
	Thread t = (Thread)threads.elementAt(i);
	t.stop();
      }
    }

    public String toString() {
      return "AggTPSTM threadPool (size="+numThreads()+") for <"+stagename+">";
    }

  }

  /**
   * Internal class implementing a thread governor - analyses appThread
   * queue lengths and adjusts thread pool sizes accordingly.
   */
  class governorThread implements Runnable {

    public void run() {
      if (DEBUG) System.err.println("AggTPSTM Governor: starting");

      while (true) {
	adjustThreadPools();
	try {
	  Thread.currentThread().sleep(governorDelay);
	} catch (InterruptedException ie) {
	  // Ignore
	}
      }
    }

    private void adjustThreadPools() {
      // Really dumb algorithm for now
      for (int i = 0; i < threadpools.size(); i++) {
	threadPool pool = (threadPool)threadpools.elementAt(i);

	if (DEBUG) System.err.println("AggTPSTM Governor: Inspecting "+pool);

	// Only adjust pools pulling data from a SourceIF/SinkIF pair
	if (pool.source instanceof SinkIF) {
	  SinkIF sink = (SinkIF)pool.source;
	  int sz = sink.size();
	  if (DEBUG) System.err.println("AggTPSTM Governor: size "+sz+", thresh "+governorThreshold);
	  if (sz == governorThreshold) {
	    // Queue is full, add a thread
	    int numt = pool.numThreads();
	    if (numt < governorMaxThreads) {
	      System.err.println("AggTPSTM Governor: Adding thread to pool "+pool);
	      pool.addThreads(1, true);
	    } else {
	      if (DEBUG) System.err.println("AggTPSTM Governor: Pool "+pool+" already at max");
	    }
	  }
	}
      }
    }
  }

}

