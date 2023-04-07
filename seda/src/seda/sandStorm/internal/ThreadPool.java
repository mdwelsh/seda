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
import seda.sandStorm.api.SandstormConfigIF;
import seda.sandStorm.api.internal.StageWrapperIF;

import java.util.Vector;

/**
 * ThreadPool is a generic class which provides a thread pool.
 * 
 * @author   Matt Welsh
 */

public class ThreadPool implements ProfilableIF {

  private static final boolean DEBUG = false;

  private StageWrapperIF stage;
  private ManagerIF mgr;
  private String poolname;
  private ThreadGroup pooltg;
  private Runnable runnable;
  private Vector threads, stoppedThreads;

  int minThreads, maxThreads;

  private int blockTime = 1000; 
  private int idleTimeThreshold;

  /**
   * Create a thread pool for the given stage, manager and runnable,
   * with the thread pool controller determining the number of threads
   * used.
   */
  public ThreadPool(StageWrapperIF stage, ManagerIF mgr, Runnable runnable) {
    this.stage = stage;
    this.poolname = stage.getStage().getName();
    this.mgr = mgr;
    this.runnable = runnable;

    SandstormConfigIF config = mgr.getConfig();
    threads = new Vector();
    stoppedThreads = new Vector();

    // First look for stages.[stageName] options, then global options
    String tag = "stages."+(stage.getStage().getName())+".threadPool.";
    String globaltag = "global.threadPool.";

    int initialSize = config.getInt(tag+"initialThreads");
    if (initialSize < 1) {
      initialSize = config.getInt(globaltag+"initialThreads");
      if (initialSize < 1) initialSize = 1;
    }
    minThreads = config.getInt(tag+"minThreads");
    if (minThreads < 1) {
      minThreads = config.getInt(globaltag+"minThreads");
      if (minThreads < 1) minThreads = 1;
    }
    maxThreads = config.getInt(tag+"maxThreads");
    if (maxThreads < 1) {
      maxThreads = config.getInt(globaltag+"maxThreads");
      if (maxThreads < 1) maxThreads = -1; // Infinite
    }

    this.blockTime = config.getInt(tag+"blockTime",
	config.getInt(globaltag+"blockTime", blockTime));
    this.idleTimeThreshold = config.getInt(tag+"sizeController.idleTimeThreshold",
	config.getInt(globaltag+"sizeController.idleTimeThreshold", blockTime));

    System.err.println("TP <"+poolname+">: initial "+initialSize+", min "+minThreads+", max "+maxThreads+", blockTime "+blockTime+", idleTime "+idleTimeThreshold);

    addThreads(initialSize, false);
    mgr.getProfiler().add("ThreadPool <"+poolname+">", this);
    pooltg = new ThreadGroup("TP <"+poolname+">");
  }

  /**
   * Create a thread pool with the given name, manager, runnable, 
   * and thread sizing parameters.
   */
  public ThreadPool(StageWrapperIF stage, ManagerIF mgr, Runnable runnable,
      int initialThreads, int minThreads, int maxThreads, int blockTime, int idleTimeThreshold) {
    this.stage = stage;
    this.poolname = stage.getStage().getName();
    this.mgr = mgr;
    this.runnable = runnable;

    SandstormConfigIF config = mgr.getConfig();
    threads = new Vector();
    stoppedThreads = new Vector();
    if (initialThreads < 1) initialThreads = 1;
    this.minThreads = minThreads;
    if (this.minThreads < 1) this.minThreads = 1;
    this.maxThreads = maxThreads;
    //if (this.maxThreads < 1) this.maxThreads = initialThreads;
    this.blockTime = blockTime;
    this.idleTimeThreshold = idleTimeThreshold;

    System.err.println("TP <"+poolname+">: initial "+initialThreads+", min "+minThreads+", max "+maxThreads+", blockTime "+blockTime+", idleTime "+idleTimeThreshold);

    addThreads(initialThreads, false);
    mgr.getProfiler().add("ThreadPool <"+poolname+">", this);
    pooltg = new ThreadGroup("TP <"+poolname+">");
  }

  /**
   * Create a thread pool with the given name, manager, runnable, 
   * and a fixed number of threads.
   */
  public ThreadPool(StageWrapperIF stage, ManagerIF mgr, Runnable runnable,
      int numThreads) {
    this.stage = stage;
    this.poolname = stage.getStage().getName();
    this.mgr = mgr;
    this.runnable = runnable;

    SandstormConfigIF config = mgr.getConfig();
    threads = new Vector();
    stoppedThreads = new Vector();
    maxThreads = minThreads = numThreads;

    System.err.println("TP <"+poolname+">: initial "+numThreads+", min "+minThreads+", max "+maxThreads+", blockTime "+blockTime+", idleTime "+idleTimeThreshold);

    addThreads(numThreads, false);
    mgr.getProfiler().add("ThreadPool <"+poolname+">", this);
    pooltg = new ThreadGroup("TP <"+poolname+">");
  }

  /**
   * Start the thread pool.
   */
  public void start() {
    System.err.println("TP <"+poolname+">: Starting "+numThreads()+" threads");
    for (int i = 0; i < threads.size(); i++) {
      Thread t = (Thread)threads.elementAt(i);
      t.start();
    }
  }

  /**
   * Stop the thread pool.
   */
  public void stop() {
      //          pooltg.stop();
      for (int i = 0; i < threads.size(); i++) {
	  Thread t = (Thread)threads.elementAt(i);
	  t.interrupt();
      }
  }

  /**
   * Add threads to this pool.
   */
  void addThreads(int num, boolean start) {
    synchronized (this) {
      int numToAdd;
      if (maxThreads < 0) {
	numToAdd = num;
      } else {
	int numTotal = Math.min(maxThreads, numThreads()+num);
	numToAdd = numTotal - numThreads();
      }
      if ((maxThreads < 0) || (numToAdd < maxThreads)) {
	System.err.println("TP <"+poolname+">: Adding "+numToAdd+" threads to pool, size "+(numThreads()+numToAdd));
      }
      for (int i = 0; i < numToAdd; i++) {
    	String name = "TP-"+numThreads()+" <"+poolname+">";
	Thread t = new Thread(pooltg, runnable, name);
	threads.addElement(t);
	mgr.getProfiler().getGraphProfiler().addThread(t, stage);
	if (start) t.start();
      }
    }
  }

  /**
   * Remove threads from pool.
   */
  void removeThreads(int num) {
    System.err.print("TP <"+poolname+">: Removing "+num+" threads from pool, ");
    synchronized (this) {
      for (int i = 0; (i < num) && (numThreads() > minThreads); i++) {
	Thread t = (Thread)threads.firstElement();
	stopThread(t);
      }
    }
    System.err.println("size "+numThreads());
  }

  /**
   * Cause the given thread to stop execution.
   */
  void stopThread(Thread t) {
    synchronized (this) {
      threads.removeElement(t);
      stoppedThreads.addElement(t);
    }
    System.err.println("TP <"+poolname+">: stopping thread, size "+numThreads());
  }

  /**
   * Return the number of threads in this pool. 
   */
  int numThreads() {
    synchronized (this) {
      return threads.size();
    }
  }

  /**
   * Used by a thread to determine its queue block time.
   */
  public long getBlockTime() {
    return blockTime;
  }

  /**
   * Used by a thread to determine whether it should exit.
   */
  public boolean timeToStop(long idleTime) {
    synchronized (this) {
      if ((idleTime > idleTimeThreshold) && (numThreads() > minThreads)) {
	stopThread(Thread.currentThread());
      }
      if (stoppedThreads.contains(Thread.currentThread())) return true;
    } 
    return false;
  }

  public String toString() {
    return "TP (size="+numThreads()+") for <"+poolname+">";
  }

  public String getName() {
    return "ThreadPool <"+poolname+">";
  }

  public int profileSize() {
    return numThreads();
  }

}

