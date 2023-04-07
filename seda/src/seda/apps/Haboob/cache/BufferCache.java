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

package seda.apps.Haboob.cache;

import seda.apps.Haboob.HaboobConst;
import seda.apps.Haboob.HaboobStats;
import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.ssLinkedList;
import seda.sandStorm.lib.aDisk.AFile;
import seda.sandStorm.lib.aDisk.AFileEOFReached;
import seda.sandStorm.lib.aDisk.AFileIOCompleted;
import seda.sandStorm.lib.aDisk.AFileStat;
import seda.sandStorm.lib.http.*;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * This implementation of the Haboob cache maintains a fixed-size set
 * of buffers that cache recently-accessed Web pages. It turns out this
 * approach does not work well -- see PageCacheSized for a better
 * alternative. (This implementation may be broken.)
 */
public class BufferCache implements EventHandlerIF, HaboobConst {

  private static final boolean DEBUG = false;

  // Don't actually read file; just store empty buffer in cache
  private static final boolean DEBUG_NO_FILE_READ = true;
  // Don't even stat file; just allocate buffer of fixed size
  private static final boolean DEBUG_NO_FILE_READ_SAMESIZE = true;
  // Send static httpOKResponse coupled with buffer
  private static final boolean DEBUG_NO_FILE_READ_RESP = true;
  // Send static httpOKResponse coupled with buffer, but payload and
  // header separate
  private static final boolean DEBUG_NO_FILE_READ_RESP_TWOWRITES = true;

  private String DEFAULT_URL;
  private String ROOT_DIR;

  private SinkIF mysink, sendSink;
  private Hashtable aFileTbl;  // Map aFile -> outstandingRead
  private int bufferSize;
  private int numBuffers;
  private ssLinkedList freeResps; // List of free static responses
  private ssLinkedList freeBuffers; // List of free buffers
  private ssLinkedList waitingRequests; // Requests waiting for free buffer

  private Hashtable mimeTbl; // Filename extension -> MIME type
  private static final String defaultMimeType = "text/plain";

  public void init(ConfigDataIF config) throws Exception {
    mysink = config.getStage().getSink();
    sendSink = config.getManager().getStage(HTTP_SEND_STAGE).getSink();
    aFileTbl = new Hashtable();

    mimeTbl = new Hashtable();
    mimeTbl.put(".html", "text/html");
    mimeTbl.put(".gif", "image/gif");
    mimeTbl.put(".jpg", "image/jpeg");
    mimeTbl.put(".jpeg", "image/jpeg");
    mimeTbl.put(".pdf", "application/pdf");

    DEFAULT_URL = config.getString("defaultURL");
    if (DEFAULT_URL == null) throw new IllegalArgumentException("Must specify defaultURL");
    ROOT_DIR = config.getString("rootDir");
    if (ROOT_DIR == null) throw new IllegalArgumentException("Must specify rootDir");

    bufferSize = config.getInt("bufferSize");
    if (bufferSize == -1) throw new IllegalArgumentException("Must specify bufferSize");
    numBuffers = config.getInt("numBuffers");
    if (numBuffers == -1) throw new IllegalArgumentException("Must specify numBuffers");

    // Allocate buffer pool
    freeBuffers = new ssLinkedList();
    freeResps = new ssLinkedList();
    for (int i = 0; i < numBuffers; i++) {
      BufferElement buf;
      if (DEBUG_NO_FILE_READ_RESP) {
	if (DEBUG_NO_FILE_READ_RESP_TWOWRITES) {
	  buf = new BufferElement(bufferSize);
	  httpOKResponse resp = new httpOKResponse("text/html", buf);
	  buf.userTag = resp;
	  freeResps.add_to_tail(resp);
	} else {
	  httpOKResponse resp = new httpOKResponse("text/html", bufferSize, mysink);
	  buf = resp.getPayload();
	  freeResps.add_to_tail(resp);
	  resp.getBuffers(true)[0].userTag = resp;
	}
      } else {
	buf = new BufferElement(bufferSize);
      }
      buf.compQ = mysink;
      freeBuffers.add_to_tail(buf);
    }
    waitingRequests = new ssLinkedList();
    System.err.println("BufferCache: Started with "+numBuffers+" buffers of "+bufferSize+" bytes each");
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("BufferCache: GOT QEL: "+item);

    if (item instanceof httpRequest) {
      HaboobStats.numRequests++;

      httpRequest req = (httpRequest)item;
      if (req.getRequest() != httpRequest.REQUEST_GET) {
	HaboobStats.numErrors++;
	sendSink.enqueue_lossy(new httpResponder(new httpBadRequestResponse(req, "Only GET requests supported at this time"), req, true));
	return;
      }

      handleRequest(req);

    } else if (item instanceof AFileIOCompleted) {
      AFileIOCompleted comp = (AFileIOCompleted)item;
      AFile af = comp.getFile();
      outstandingRead or = (outstandingRead)aFileTbl.get(af);
      // Could be null if the connection was closed while we did a file read
      if (or != null) or.processFileIO(comp);

    } else if (item instanceof SinkDrainedEvent) {
      if (DEBUG) System.err.println("BufferCache: Got SDE: "+item);

      SinkDrainedEvent sde = (SinkDrainedEvent)item;
      BufferElement buf = (BufferElement)(sde.element);
      outstandingRead or = null;
      if (!DEBUG_NO_FILE_READ_SAMESIZE) {
        or = (outstandingRead)(buf.userTag);
      } else if (DEBUG_NO_FILE_READ_RESP) {
	httpOKResponse resp = (httpOKResponse)(buf.userTag);
	freeResps.add_to_tail(resp);
      } else {
	freeBuffer(buf);
      }
      if (DEBUG_NO_FILE_READ_SAMESIZE || or.processNetIO(sde)) {
	if (DEBUG) System.err.println("BufferCache: Finished with buffer, "+waitingRequests.size()+" waiters, "+freeBuffers.size()+" freebufs");
	// Finished with buffer, fire off next waiter
	httpRequest req;
	boolean ret = false;
	do {
	  req = (httpRequest)waitingRequests.remove_head();
	  if (req != null) {
	    if (DEBUG) System.err.println("BufferCache: Firing off waiter "+req);
	    ret = handleRequest(req);
	  } 
	} while ((req != null) && (ret == false));
      }

    } else if (item instanceof AFileEOFReached) {
      // Ignore

    } else if (item instanceof SinkClosedEvent) {
      SinkClosedEvent sce = (SinkClosedEvent)item;
      if (sce.sink instanceof httpConnection) {
	httpConnection hc = (httpConnection)sce.sink;
	outstandingRead or = (outstandingRead)hc.userTag;
	if (or != null) or.cleanup();
      }

    } else {
      System.err.println("BufferCache: Got unknown event type: "+item);
    }

  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++) {
      handleEvent(items[i]);
    }
  }

  // Return false if error occurs
  private boolean handleRequest(httpRequest req) {
    String url;
    String fname;

    url = req.getURL();
    fname = ROOT_DIR + url;

    AFile af = null;
    AFileStat stat;
    httpOKResponse resp;
    outstandingRead or;
    BufferElement buf;

    if (DEBUG_NO_FILE_READ_RESP) {
      // Send static response
      resp = (httpOKResponse)freeResps.remove_head();
      if (resp == null) {
	System.err.println("BufferCache: No free responses -- waiting!");
	waitingRequests.add_to_tail(req);
	return true;
      }
      sendSink.enqueue_lossy(new httpResponder(resp, req, false));
      return true;
    }

    // Try to get a free buffer
    buf = (BufferElement)freeBuffers.remove_head();
    if (buf == null) {
      if (DEBUG) System.err.println("BufferCache: No free buffers, waiting.");
      waitingRequests.add_to_tail(req);
      return true;
    }
    if (DEBUG) System.err.println("BufferCache: Got free buffer, "+waitingRequests.size()+" waiters, "+freeBuffers.size()+" freebufs");

    if (DEBUG_NO_FILE_READ && DEBUG_NO_FILE_READ_SAMESIZE) {
      // Just send response with the free buffer 
      resp = new httpOKResponse("text/html", buf);
      sendSink.enqueue_lossy(new httpResponder(resp, req, false));
      // Will free buffer and fire off waiters when net I/O completes
      return true;
    }

    // Open file and stat it to determine size
    try {
      af = new AFile(fname, mysink, false, true);
      stat = af.stat();
      if (stat.isDirectory) {
	af.close();
	fname = fname+"/"+DEFAULT_URL;
	af = new AFile(fname, mysink, false, true);
	stat = af.stat();
      }
    } catch (IOException ioe) {
      // File not found
      System.err.println("BufferCache: Could not open file "+fname+": "+ioe);
      HaboobStats.numErrors++;
      httpNotFoundResponse notfound = new httpNotFoundResponse(req, ioe.getMessage());
      sendSink.enqueue_lossy(new httpResponder(notfound, req, true));
      freeBuffer(buf);
      return false;
    }

    // Create OR and fire off read
    or = new outstandingRead(req, af, buf);
    return true;
  }

  private void freeBuffer(BufferElement buf) {
    freeBuffers.add_to_tail(buf);
  }

  private String getMimeType(String url) {
    Enumeration e = mimeTbl.keys();
    while (e.hasMoreElements()) {
      String key = (String)e.nextElement();
      if (url.endsWith(key)) return (String)mimeTbl.get(key);
    }
    return defaultMimeType;
  }

  private class outstandingRead {
    httpRequest request;
    httpOKResponse response;
    AFile af;
    BufferElement buf, bufToSend;
    int cur_file_offset;
    int file_size;
    ssLinkedList waiting;
    boolean first = true;

    outstandingRead(httpRequest req, AFile af, BufferElement buf) {
      this.request = req;
      this.af = af;
      this.file_size = (int)(af.stat().length);
      this.cur_file_offset = 0;
      this.response = new httpOKResponse(getMimeType(af.getFilename()), null, file_size);
      this.buf = buf;
      req.getConnection().userTag = this;
      buf.userTag = this;

      if (!DEBUG_NO_FILE_READ) {
	aFileTbl.put(af, this);
	if (DEBUG) System.err.println("BufferCache: OR (file_size "+file_size+") doing initial read");
	try {
	  af.read(buf);
	} catch (SinkException se) {
	  // Should not happen!
	  System.err.println("BufferCache: Warning: Got SE doing af.read(): "+se);
	  cleanup();
	}
      } else {
	// DEBUG_NO_FILE_READ: Fake out file read
	AFileIOCompleted fake = new AFileIOCompleted(null, 
	    Math.min(file_size, buf.size));
	processFileIO(fake);
      }
    }

    // Process an AFile IO Completion on this file
    void processFileIO(AFileIOCompleted comp) {
      // Can issue next socket write

      if (DEBUG) System.err.println("BufferCache: Processing IOCompletion "+comp);
      int l = comp.sizeCompleted;
      if (l < buf.size) {
	if (DEBUG) System.err.println("BufferCache: Creating bufToSize, size "+l);
	bufToSend = new BufferElement(buf.data, 0, l);
	bufToSend.userTag = this;
	bufToSend.compQ = mysink;
      } else {
	if (DEBUG) System.err.println("BufferCache: Using original buffer");
	bufToSend = buf;
      }
      if (DEBUG) System.err.println("BufferCache: Setting payload");
      response.setPayload(bufToSend);

      httpResponder respd;
      if (first) {
	// Send header with response
	if (DEBUG) System.err.println("BufferCache: OR (file_size "+file_size+") sending initial response, size "+l);
	respd = new httpResponder(response, request, false, true);
	first = false;
      } else {
	// DON'T send header with response
	if (DEBUG) System.err.println("BufferCache: OR (file_size "+file_size+") sending response, size "+l);
	respd = new httpResponder(response, request, false, false);
      }
      sendSink.enqueue_lossy(respd);
    }

    // Process network I/O completion
    boolean processNetIO(SinkDrainedEvent sde) {
      // Issue next file read?
      if (DEBUG) System.err.println("BufferCache: OR (file_size "+file_size+") incrementing cur_file_offset by "+bufToSend.size);
      cur_file_offset += bufToSend.size;
      if (cur_file_offset < file_size) {

	if (!DEBUG_NO_FILE_READ) {
	  try {
	    if (DEBUG) System.err.println("BufferCache: OR (file_size "+file_size+") issuing next read");
	    af.read(buf);
	  } catch (SinkException se) {
	    // Should not happen!
	    System.err.println("BufferCache: Warning: Got SE doing af.read(): "+se);
	    cleanup();
	    return true;
	  }
	  return false;
	} else {
	  // DEBUG_NO_FILE_READ: Fake out file read
	  AFileIOCompleted fake = new AFileIOCompleted(null, 
	      Math.min(file_size - cur_file_offset, buf.size));
	  processFileIO(fake);
	  return false;
	}

      } else {
	// We are done with this file
	cleanup();
	return true;
      }
    }

    void cleanup() {
      if (DEBUG) System.err.println("BufferCache: OR (file_size "+file_size+") finished");
      buf.userTag = null;
      request.getConnection().userTag = null;
      freeBuffer(buf);
      af.close();
      aFileTbl.remove(af);
    }

  }


}

