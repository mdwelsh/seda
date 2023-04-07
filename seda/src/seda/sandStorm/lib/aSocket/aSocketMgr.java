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

package seda.sandStorm.lib.aSocket;

import seda.sandStorm.api.*;
import seda.sandStorm.api.internal.SystemManagerIF;
import seda.sandStorm.api.internal.ThreadManagerIF;
import seda.sandStorm.internal.ConfigData;
import seda.sandStorm.main.Sandstorm;
import seda.sandStorm.main.SandstormConfig;
import seda.util.Tracer;

/**
 * The aSocketMgr is an internal class used to provide an interface between
 * the Sandstorm runtime and the aSocket library. Applications should not
 * make use of this class.
 *
 * @author Matt Welsh
 */
public class aSocketMgr {

  private static final boolean DEBUG = false;
  private static final boolean PROFILE = false;

  private static ThreadManagerIF aSocketTM, aSocketRCTM;
  private static SinkIF read_sink;
  private static SinkIF listen_sink;
  private static SinkIF write_sink;

  private static ReadEventHandler read_handler;

  private static Object init_lock = new Object();
  private static boolean initialized = false;

  static boolean USE_NIO = false;
  private static aSocketImplFactory factory;

  public static Tracer tracer;

  /**
   * Called at startup time by the Sandstorm runtime.
   */
  public static void initialize(ManagerIF mgr, SystemManagerIF sysmgr) throws Exception {

    synchronized (init_lock) {
      if (PROFILE) tracer = new Tracer("aSocketMgr");

      SandstormConfigIF cfg = mgr.getConfig();

      String provider = cfg.getString("global.aSocket.provider");
      if (provider == null) {
          throw new RuntimeException("aSocketMgr: Must specify either " +
                "'NIO' or 'NBIO' for global.aSocket.provider");
      }

      if (provider.equals("NIO")) {
          USE_NIO = true;
	  System.err.println("aSocket layer using JDK1.4 java.nio package");
      } else if (provider.equals("NBIO")) {
          USE_NIO = false;
	  System.err.println("aSocket layer using NBIO package");
      } else {
          throw new RuntimeException("aSocketMgr: Must specify either " +
                "'NIO' or 'NBIO' for global.aSocket.provider");
      }

      try {
	factory = aSocketImplFactory.getFactory();
      } catch (Exception e) {
	throw new RuntimeException("aSocketMgr: Cannot create aSocketImplFactory: "+e);
      }

      aSocketTM = new aSocketThreadManager(mgr);
      sysmgr.addThreadManager("aSocket", aSocketTM);

      ReadEventHandler revh = new ReadEventHandler();
      read_handler = revh;	// save this for calls to interruptSelect.
      aSocketStageWrapper rsw;
      if (cfg.getBoolean("global.aSocket.governor.enable")) {
	aSocketRCTM = new aSocketRCTMSleep(mgr);
	sysmgr.addThreadManager("aSocketRCTM", aSocketRCTM);
	rsw = new aSocketStageWrapper(mgr, "aSocket ReadStage", 
  	    revh, new ConfigData(mgr), aSocketRCTM);
      } else {
	rsw = new aSocketStageWrapper(mgr, "aSocket ReadStage", 
	    revh, new ConfigData(mgr), aSocketTM);
      }

      StageIF readStage = sysmgr.createStage(rsw, true);
      read_sink = readStage.getSink();

      ListenEventHandler levh = new ListenEventHandler();
      aSocketStageWrapper lsw = new aSocketStageWrapper(mgr, "aSocket ListenStage", 
	  levh, new ConfigData(mgr), aSocketTM);
      StageIF listenStage = sysmgr.createStage(lsw, true);
      listen_sink = listenStage.getSink();

      WriteEventHandler wevh = new WriteEventHandler();
      aSocketStageWrapper wsw = new aSocketStageWrapper(mgr, "aSocket WriteStage", 
	  wevh, new ConfigData(mgr), aSocketTM);
      StageIF writeStage = sysmgr.createStage(wsw, true);
      write_sink = writeStage.getSink();

      initialized = true;
    }
  }

  /** 
   * Ensure that the aSocket layer is initialized, in case the library
   * is being used in standalone mode.
   */
  static void init() {
    synchronized (init_lock) {
      // When invoked in standalone mode
      if (!initialized) {
       	try {
	  Sandstorm ss = Sandstorm.getSandstorm();
	  if (ss != null) {
	    initialize(ss.getManager(), ss.getSystemManager());
	  } else {
	    SandstormConfig cfg = new SandstormConfig();
	    ss = new Sandstorm(cfg);
	  }
   	} catch (Exception e) {
  	  System.err.println("aSocketMgr: Warning: Initialization failed: "+e);
  	  e.printStackTrace();
  	  return;
   	}
      }
    }
  }

  static aSocketImplFactory getFactory() {
    return factory;
  }

  static public void enqueueRequest(aSocketRequest req) {
    if (PROFILE) tracer.trace("enqueueRequest called");
    init();

    if ((req instanceof ATcpWriteRequest) ||
	(req instanceof ATcpConnectRequest) ||
	(req instanceof ATcpFlushRequest) ||
	(req instanceof ATcpCloseRequest) ||
	(req instanceof AUdpWriteRequest) ||
	(req instanceof AUdpCloseRequest) ||
	(req instanceof AUdpFlushRequest) ||
	(req instanceof AUdpConnectRequest) ||
	(req instanceof AUdpDisconnectRequest)) {

      try {
	if (PROFILE) WriteEventHandler.tracer.trace("write_sink enqueue");
	write_sink.enqueue(req);
	//Thread.currentThread().yield(); // XXX MDW TESTING
	if (PROFILE) WriteEventHandler.tracer.trace("write_sink enqueue done");
      } catch (SinkException se) {
	System.err.println("aSocketMgr.enqueueRequest: Warning: Got SinkException "+se);
	System.err.println("aSocketMgr.enqueueRequest: This is a bug - contact <mdw@cs.berkeley.edu>");
      }

    } else if ((req instanceof ATcpStartReadRequest) ||
               (req instanceof AUdpStartReadRequest)) {

      try {
	read_sink.enqueue(req);
      } catch (SinkException se) {
	System.err.println("aSocketMgr.enqueueRequest: Warning: Got SinkException "+se);
	System.err.println("aSocketMgr.enqueueRequest: This is a bug - contact <mdw@cs.berkeley.edu>");
      }
      if (req instanceof ATcpStartReadRequest) {
	  ATcpStartReadRequest srreq = (ATcpStartReadRequest)req;
	  SockState ss = srreq.conn.sockState;
      } else if (req instanceof AUdpStartReadRequest) {
	  AUdpStartReadRequest srreq = (AUdpStartReadRequest)req;
	  DatagramSockState ss = srreq.sock.sockState;
      }
      read_handler.interruptSelect();

    } else if ((req instanceof ATcpListenRequest) ||
	(req instanceof ATcpSuspendAcceptRequest) ||
	(req instanceof ATcpResumeAcceptRequest) ||
	(req instanceof ATcpCloseServerRequest)) {

      try {
	listen_sink.enqueue(req);
      } catch (SinkException se) {
	System.err.println("aSocketMgr.enqueueRequest: Warning: Got SinkException "+se);
	System.err.println("aSocketMgr.enqueueRequest: This is a bug - contact <mdw@cs.berkeley.edu>");
      }

    } else {
      throw new IllegalArgumentException("Bad request type "+req);
    }
    if (PROFILE) tracer.trace("enqueueRequest done");
  }
}

