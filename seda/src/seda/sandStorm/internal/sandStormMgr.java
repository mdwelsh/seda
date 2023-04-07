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

import seda.sandStorm.api.*;
import seda.sandStorm.api.internal.StageWrapperIF;
import seda.sandStorm.api.internal.SystemManagerIF;
import seda.sandStorm.api.internal.ThreadManagerIF;
import seda.sandStorm.lib.aDisk.AFileMgr;
import seda.sandStorm.lib.aSocket.aSocketMgr;
import seda.sandStorm.main.SandstormConfig;
import seda.sandStorm.main.stageDescr;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class provides management functionality for the Sandstorm 
 * runtime system. It is responsible for initializing the system,
 * creating and registering stages and thread managers, and other
 * administrative functions. Stages and thread managers can interact
 * with this class through the ManagerIF and SystemManagerIF interfaces;
 * this class should not be used directly.
 *
 * @author Matt Welsh
 * @see seda.sandStorm.api.ManagerIF
 * @see seda.sandStorm.api.internal.SystemManagerIF
 * 
 */
public class sandStormMgr implements ManagerIF, SystemManagerIF, sandStormConst {

  private ThreadManagerIF defaulttm;
  private Hashtable tmtbl;

  private SandstormConfig mgrconfig;
  private Hashtable stagetbl;
  private Vector stagestoinit;
  private boolean started = false;
  private sandStormProfiler profiler;
  private SignalMgr signalMgr;
  private boolean crashOnException = false;

  /**
   * Create a sandStormMgr which reads its configuration from the 
   * given file.
   */
  public sandStormMgr(SandstormConfig mgrconfig) throws Exception {
    this.mgrconfig = mgrconfig;

    stagetbl = new Hashtable();
    tmtbl = new Hashtable();
    stagestoinit = new Vector();
    signalMgr = new SignalMgr();

    crashOnException = mgrconfig.getBoolean("global.crashOnException");
    String dtm = mgrconfig.getString("global.defaultThreadManager");
    if (dtm == null) {
      throw new IllegalArgumentException("No threadmanager specified by configuration");
    }

    if (dtm.equals(SandstormConfig.THREADMGR_TPPTM)) {
      //throw new Error("TPPThreadManager is no longer supported.");
      defaulttm = new TPPThreadManager(mgrconfig);
    } else if (dtm.equals(SandstormConfig.THREADMGR_TPSTM)) {
      defaulttm = new TPSThreadManager(this);
    } else if (dtm.equals(SandstormConfig.THREADMGR_AggTPSTM)) {
      throw new Error("AggTPSThreadManager is no longer supported.");
      /* defaulttm = new AggTPSThreadManager(mgrconfig); */
    } else {
      throw new IllegalArgumentException("Bad threadmanager specified by configuration: "+dtm);
    }

    tmtbl.put("default", defaulttm);

    initialize_io();
    loadInitialStages();
  }

  /**
   * Start the manager.
   */
  public void start() {
    started = true;
    System.err.println("Sandstorm: Initializing stages");
    initStages();

    // Let the threads start
    try {
      System.err.println("Sandstorm: Waiting for all components to start...");
      Thread.sleep(500);
    } catch (InterruptedException ie) {
      // Ignore
    }

    System.err.println("\nSandstorm: Ready.\n");
  }

  /**
   * Stop the manager.
   */
  public void stop() {
    Enumeration e = tmtbl.keys();
    while (e.hasMoreElements()) {
      String name = (String)e.nextElement();
      ThreadManagerIF tm = (ThreadManagerIF)tmtbl.get(name);
      System.err.println("Sandstorm: Stopping ThreadManager "+name);
      tm.deregisterAll();
    }

    System.err.println("Sandstorm: Shutting down stages");
    destroyStages();
    started = false;
  }

  /**
   * Return a handle to given stage.
   */
  public StageIF getStage(String stagename) throws NoSuchStageException {
    if (stagename == null) throw new NoSuchStageException("no such stage: null");
    StageWrapperIF wrapper = (StageWrapperIF)stagetbl.get(stagename);
    if (wrapper == null) throw new NoSuchStageException("no such stage: "+stagename);
    return wrapper.getStage();
  }

  // Initialize the I/O layer
  private void initialize_io() throws Exception {

    // Create profiler even if disabled
    profiler = new sandStormProfiler(this);

    if (mgrconfig.getBoolean("global.profile.enable")) {
      System.err.println("Sandstorm: Starting profiler");
      profiler.start();
    }

    if (mgrconfig.getBoolean("global.aSocket.enable")) {
      System.err.println("Sandstorm: Starting aSocket layer");
      aSocketMgr.initialize(this, this);
    } 

    if (mgrconfig.getBoolean("global.aDisk.enable")) {
      System.err.println("Sandstorm: Starting aDisk layer");
      AFileMgr.initialize(this, this);
    }
  }

  // Load stages as specified in the SandstormConfig.
  private void loadInitialStages() throws Exception {
    Enumeration e = mgrconfig.getStages();
    if (e == null) return;
    while (e.hasMoreElements()) {
      stageDescr descr = (stageDescr)e.nextElement();
      loadStage(descr);
    }
  }

  /**
   * Return the default thread manager.
   */
  public ThreadManagerIF getThreadManager() {
    return defaulttm;
  }

  /**
   * Return the thread manager with the given name.
   */
  public ThreadManagerIF getThreadManager(String name) {
    return (ThreadManagerIF)tmtbl.get(name);
  }

  /**
   * Add a thread manager with the given name.
   */
  public void addThreadManager(String name, ThreadManagerIF tm) {
    tmtbl.put(name, tm);
  }

  // Load a stage from the given classname with the given config.
  private void loadStage(stageDescr descr) throws Exception {
    String stagename = descr.stageName;
    String classname = descr.className;
    ConfigData config = new ConfigData(this, descr.initargs);
    Class theclass = Class.forName(classname);
    EventHandlerIF evHandler = (EventHandlerIF)theclass.newInstance();
    System.out.println("Sandstorm: Loaded "+stagename+" from "+classname);

    StageWrapper wrapper = new StageWrapper((ManagerIF)this, 
	stagename, evHandler, config, defaulttm, descr.queueThreshold);

    createStage(wrapper, false);
  }

  /**
   * Create a stage with the given name from the given event handler with
   * the given initial arguments.
   */
  public StageIF createStage(String stageName, EventHandlerIF evHandler, 
      String initargs[]) throws Exception {
    ConfigDataIF config = new ConfigData(this, initargs);
    if (stagetbl.get(stageName) != null) {
      // Come up with a better (random) name
      stageName = stageName+"-"+stagetbl.size();
    }
    StageWrapperIF wrapper = new StageWrapper((ManagerIF)this, 
	stageName, evHandler, config, defaulttm);

    return createStage(wrapper, true);
  }

  /**
   * Create a stage from the given stage wrapper. 
   * If 'initialize' is true, initialize this stage immediately.
   */
  public StageIF createStage(StageWrapperIF wrapper, boolean initialize) throws Exception {
    String name = wrapper.getStage().getName();
    if (stagetbl.get(name) != null) {
      throw new StageNameAlreadyBoundException("Stage name "+name+" already in use");
    }
    stagetbl.put(name, wrapper);

    if (mgrconfig.getBoolean("global.profile.enable")) {
      profiler.add(wrapper.getStage().getName()+" queueLength",
	  (ProfilableIF)wrapper.getStage().getSink());
    }

    if (initialize) {
      try {
	wrapper.init();
      } catch (Exception e) {
	System.err.println("Sandstorm: Got exception initializing stage "+name);
	e.printStackTrace();
	if (crashOnException) {
	  System.err.println("Sandstorm: Crashing runtime due to exception - goodbye");
	  System.exit(-1);
	}
	throw e;
      }
    } else {
      stagestoinit.addElement(wrapper);
    }
    return wrapper.getStage();
  } 

  /**
   * Return the system profiler.
   */
  public ProfilerIF getProfiler() {
    return profiler;
  }

  /**
   * Return the system signal manager.
   */
  public SignalMgrIF getSignalMgr() {
    return signalMgr;
  }

  /**
   * Return the SandstormConfig used to initialize this manager.
   * Actually returns a copy of the SandstormConfig; this prevents
   * options from being changed once the system has been initialized.
   */
  public SandstormConfigIF getConfig() {
    return mgrconfig.getCopy();
  }

  // Initialize all stages
  private void initStages() {

    for (int i = 0; i < stagestoinit.size(); i++) {
      StageWrapperIF wrapper = (StageWrapperIF)stagestoinit.elementAt(i);
      try {
	System.err.println("-- Initializing <"+wrapper.getStage().getName()+">");
	wrapper.init();
      } catch (Exception ex) {
	System.err.println("Sandstorm: Caught exception initializing stage "+wrapper.getStage().getName()+": "+ex);
	ex.printStackTrace();
	if (crashOnException) {
	  System.err.println("Sandstorm: Crashing runtime due to exception - goodbye");
	  System.exit(-1);
	}
      }
    }

    signalMgr.trigger(new StagesInitializedSignal());
  }

  public void destroyStage(String stagename) throws Exception {

    if (stagename == null) throw new NoSuchStageException("no such stage: null");
    StageWrapperIF wrapper = (StageWrapperIF)stagetbl.get(stagename);
    if (wrapper == null) throw new NoSuchStageException("no such stage: "+stagename);

    // remove from all data structures
    stagetbl.remove(stagename);

    // the profiler does not contain a reference to the stage itself, just to
    // the stage's sink, and it doesn't support removal anyway, so leave it
    // alone for now

    // have the wrapper destroy what's left
    wrapper.destroy();
  }

  // Destroy all stages
  private void destroyStages() {
    Enumeration e = stagetbl.elements();
    while (e.hasMoreElements()) {
      StageWrapperIF wrapper = (StageWrapperIF)e.nextElement();
      try {
	wrapper.destroy();
      } catch (Exception ex) {
	System.err.println("Sandstorm: Caught exception destroying stage "+wrapper.getStage().getName()+": "+ex);
	ex.printStackTrace();
      }
    }
  }

}

