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

package seda.sandStorm.lib.aDisk;

import seda.sandStorm.api.*;
import seda.sandStorm.api.internal.StageWrapperIF;
import seda.sandStorm.api.internal.SystemManagerIF;
import seda.sandStorm.api.internal.ThreadManagerIF;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.internal.ConfigData;
import seda.sandStorm.internal.TPSThreadManager;
import seda.sandStorm.internal.ThreadPool;
import seda.sandStorm.internal.ThreadPoolController;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * This is the ThreadManager implementation for AFileTPImpl.
 * It manages a pool of threads which perform blocking I/O
 * on disk files; this is a portable implementation and is not
 * meant to be high performance.
 *
 * @author Matt Welsh
 */
class AFileTPTM extends TPSThreadManager implements ThreadManagerIF, ProfilableIF {

  private static final boolean DEBUG = false;

  // Global queue for files with pending entries
  private FiniteQueue fileQ;
  // Count of outstanding file requests, derived from length of
  // queue of each file on fileQ
  private int numOutstandingRequests;

  // Maximum number of consecutive requests to service per file
  private static final int MAX_REQUESTS_PER_FILE = 10;
  // Block time for file queue
  private static final int QUEUE_BLOCK_TIME = 1000;

  AFileTPTM(ManagerIF mgr, SystemManagerIF sysmgr) throws Exception {
    super(mgr, false);

    if (DEBUG) System.err.println("AFileTPTM: Created");

    if (config.getBoolean("global.aDisk.threadPool.sizeController.enable")) {
      sizeController = new ThreadPoolController(mgr, 
	  config.getInt("global.aDisk.threadPool.sizeController.delay"),
	  config.getInt("global.aDisk.threadPool.sizeController.threshold"));
    }

    fileQ = new FiniteQueue();
    numOutstandingRequests = 0;
    sysmgr.addThreadManager("AFileTPTM", this);
    AFileTPStageWrapper sw = new AFileTPStageWrapper("AFileTPTM Stage",
	null, new ConfigData(mgr), this);
    StageIF theStage = sysmgr.createStage(sw, true);

    if (mgr.getProfiler() != null) {
      mgr.getProfiler().add("AFileTPTM outstanding reqs", this);
    }
  }

  /**
   * Register a stage with this thread manager.
   */
  public void register(StageWrapperIF stage) {
    // Create a single threadPool - only one stage registered with us
    AFileTPThread at = new AFileTPThread((AFileTPStageWrapper)stage);
    SandstormConfigIF config = mgr.getConfig();
    ThreadPool tp = new ThreadPool(stage, mgr, at,
	config.getInt("global.aDisk.threadPool.initialThreads"),
	config.getInt("global.aDisk.threadPool.minThreads"),
	config.getInt("global.aDisk.threadPool.maxThreads"),
	config.getInt("global.threadPool.blockTime"),
	config.getInt("global.threadPool.sizeController.idleTimeThreshold"));
    at.registerTP(tp);
    // Use numOutstandingRequests as metric
    if (sizeController != null) sizeController.register(stage, tp, this); 
    tp.start();
  }

  /**
   * Indicate that a file has pending events.
   */
  public void fileReady(AFileTPImpl impl) {
    try {
      fileQueueEntry fqe = new fileQueueEntry(impl);
      fileQ.enqueue(fqe);
      synchronized (fileQ) {
	numOutstandingRequests += fqe.size;
      }
    } catch (SinkException se) {
      throw new InternalError("AFileTPTM.fileReady() got SinkException -- this should not happen, please contact <mdw@cs.berkeley.edu>");
    }
  }

  // Return the number of outstanding elements, for profiling
  public int profileSize() {
    return numOutstandingRequests;
  }

  // Used to keep track of number of elements on fileQ
  class fileQueueEntry implements QueueElementIF {
    AFileTPImpl impl;
    int size;

    fileQueueEntry(AFileTPImpl impl) {
      this.impl = impl;
      this.size = ((SourceIF)impl.getQueue()).size();
    }
  }

  /**
   * Internal class representing a single AFileTPTM-managed thread.
   */
  class AFileTPThread extends TPSThreadManager.stageRunnable implements Runnable {

    AFileTPThread(AFileTPStageWrapper wrapper) {
      super(wrapper, null);
    }

    public void registerTP(ThreadPool tp) {
      this.tp = tp;
    }

    public void run() {
      int blockTime;
      long t1, t2;

      if (DEBUG) System.err.println(name+": starting");

      t1 = System.currentTimeMillis();

      while (true) {

	try {

	  blockTime = (int)tp.getBlockTime();

	  AFileTPImpl impl;
       	  fileQueueEntry fqe = (fileQueueEntry)fileQ.blocking_dequeue(blockTime);
	  if (fqe == null) {
	    t2 = System.currentTimeMillis();
	    if (tp.timeToStop(t2-t1)) {
	      if (DEBUG) System.err.println(name+": Exiting");
	      return;
	    }
	    continue;
	  }
	  t1 = System.currentTimeMillis();

	  impl = fqe.impl;
	  synchronized (fileQ) {
	    numOutstandingRequests -= fqe.size;
	  }

	  int n = 0;

	  while (n < MAX_REQUESTS_PER_FILE) {
	    AFileRequest req = (AFileRequest)impl.getQueue().dequeue();
	    if (req == null) break;
	    processRequest(req);
	    n++;
	  }
	  // If events still pending, place back on file queue
	  if (((SourceIF)impl.getQueue()).size() != 0) fileReady(impl);

	  Thread.yield(); // only accomplishes delay

	} catch (Exception e) {
	  System.err.println(name+": got exception "+e);
	  e.printStackTrace();
	}
      }
    }

    private void processRequest(AFileRequest req) {
      if (DEBUG) System.err.println(name+" processing request: "+req);

      // Read request
      if (req instanceof AFileReadRequest) {
	AFileReadRequest rreq = (AFileReadRequest)req;
	AFileTPImpl impl = (AFileTPImpl)rreq.getImpl();
	RandomAccessFile raf = impl.raf;
	BufferElement buf = rreq.buf;
	try {
	  int c = raf.read(buf.data, buf.offset, buf.size);
	  if (c == -1) {
	    req.complete(new AFileEOFReached(req));
	  } else if (c < buf.size) {
	    // This can occur if buf.size is less than the size of the file
	    req.complete(new AFileIOCompleted(req, c));
	    req.complete(new AFileEOFReached(req));
	  } else {
	    req.complete(new AFileIOCompleted(req, buf.size));
	  }
	} catch (IOException ioe) {
	  req.complete(new AFileIOExceptionOccurred(req, ioe));
	}

      // Write request
      } else if (req instanceof AFileWriteRequest) {
	AFileWriteRequest wreq = (AFileWriteRequest)req;
	AFileTPImpl impl = (AFileTPImpl)wreq.getImpl();
	RandomAccessFile raf = impl.raf;
	BufferElement buf = wreq.buf;
	try {
	  raf.write(buf.data, buf.offset, buf.size);
	  req.complete(new AFileIOCompleted(req, buf.size));
	} catch (IOException ioe) {
	  req.complete(new AFileIOExceptionOccurred(req, ioe));
	}

      // Seek request
      } else if (req instanceof AFileSeekRequest) {
	AFileSeekRequest sreq = (AFileSeekRequest)req;
	AFileTPImpl impl = (AFileTPImpl)sreq.getImpl();
	RandomAccessFile raf = impl.raf;
	try {
	  raf.seek(sreq.offset);
	} catch (IOException ioe) {
	  req.complete(new AFileIOExceptionOccurred(req, ioe));
	}

      // Close request
      } else if (req instanceof AFileCloseRequest) {
	AFileCloseRequest creq = (AFileCloseRequest)req;
	AFileTPImpl impl = (AFileTPImpl)creq.getImpl();
	RandomAccessFile raf = impl.raf;
	try {
	  raf.close();
	} catch (IOException ioe) {
	  req.complete(new AFileIOExceptionOccurred(req, ioe));
	}
	req.complete(new SinkClosedEvent(req.afile));

      // Flush request
      } else if (req instanceof AFileFlushRequest) {
	// Don't know how to flush an RAF
	req.complete(new SinkFlushedEvent(req.afile));

      } else {
	throw new Error("AFileTPTM.AFileTPThread.processRequest got bad request: "+req);
      }

    }
  }

}


