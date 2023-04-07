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
import seda.sandStorm.lib.aDisk.AFileIOCompleted;
import seda.sandStorm.lib.aDisk.AFileStat;
import seda.sandStorm.lib.http.*;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

/** 
 * This implementation of the Haboob "cache" does not actually
 * cache pages, but rather always reads them from files using
 * the Sandstorm AFile interface.
 */
public class AFileRead implements EventHandlerIF, HaboobConst {

  private static final boolean DEBUG = false;
  private static final boolean PROFILE = true;
  
  // Don't actually read file; just store empty buffer in cache
  private static final boolean DEBUG_NO_FILE_READ = true;
  // Don't even stat file; just allocate buffer of fixed size
  private static final boolean DEBUG_NO_FILE_READ_SAMESIZE = true;
  private static final int DEBUG_NO_FILE_READ_SAMESIZE_SIZE = 8192;

  private String DEFAULT_URL;
  private String ROOT_DIR;

  private SinkIF mysink, sendSink;
  private Hashtable aFileTbl;

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
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("AFileRead: GOT QEL: "+item);

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
      if (or == null) {
	throw new RuntimeException("AFileRead: WARNING: Got AFileIOCompleted for non-entry: "+comp);
      }
      if (comp.sizeCompleted != or.length) {
	throw new RuntimeException("AFileRead: WARNING: Got "+comp.sizeCompleted+" bytes read, expecting "+or.length);
      }
      af.close();
      aFileTbl.remove(af);
      or.done();

    } else if (item instanceof SinkClosedEvent) {
      // Ignore

    } else {
      System.err.println("AFileRead: Got unknown event type: "+item);
    }

  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++) {
      handleEvent(items[i]);
    }
  }

  private void handleRequest(httpRequest req) {
    String url;
    String fname;

    url = req.getURL();
    fname = ROOT_DIR + url;

    AFile af = null;
    AFileStat stat;
    BufferElement payload = null;
    httpOKResponse resp;
    outstandingRead or;

    if (DEBUG_NO_FILE_READ && DEBUG_NO_FILE_READ_SAMESIZE) {
      resp = new httpOKResponse(getMimeType(fname), DEBUG_NO_FILE_READ_SAMESIZE_SIZE);
      or = new outstandingRead(req, resp, null, DEBUG_NO_FILE_READ_SAMESIZE_SIZE);

    } else {

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

	resp = new httpOKResponse(getMimeType(fname), (int)stat.length);
	payload = resp.getPayload();

      } catch (IOException ioe) {
	// File not found
	System.err.println("AFileRead: Could not open file "+fname+": "+ioe);
	HaboobStats.numErrors++;
	httpNotFoundResponse notfound = new httpNotFoundResponse(req, ioe.getMessage());
	sendSink.enqueue_lossy(new httpResponder(notfound, req, true));
	return;
      }

      or = new outstandingRead(req, resp, af, (int)stat.length);
    }

    if (!DEBUG_NO_FILE_READ || !DEBUG_NO_FILE_READ_SAMESIZE) {
      aFileTbl.put(af, or);
    } 

    if (!DEBUG_NO_FILE_READ) {
      try {
       	af.read(payload);
      } catch (SinkException se) {
	// XXX Should not really happen
	System.err.println("AFileRead: Got SinkException attempting read on "+fname+": "+se);
	aFileTbl.remove(af);
	af.close();
	HaboobStats.numErrors++;
	httpNotFoundResponse notfound = new httpNotFoundResponse(req, se.getMessage());
	sendSink.enqueue_lossy(new httpResponder(notfound, req, true));
	return;
      }
    } else {
      // Pretend we got it already
      if (!DEBUG_NO_FILE_READ_SAMESIZE) {
	af.close();
	aFileTbl.remove(af);
      }
      or.done();
    }
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
    httpOKResponse response;
    int length;
    boolean pending;
    int size;
    BufferElement buf;
    AFile af;
    ssLinkedList waiting;
    String url;

    outstandingRead(httpRequest req, httpOKResponse resp, AFile af, int size) {
      this.response = resp;
      this.length = resp.getPayload().size;
      this.url = req.getURL();
      this.af = af;
      this.size = size;
      pending = true;
      waiting = new ssLinkedList();
      addWaiter(req);
    }

    synchronized void addWaiter(httpRequest req) {
      waiting.add_to_tail(req);
    }

    // Send response to all waiters when done reading
    synchronized void done() {
      if (DEBUG) System.err.println("AFileRead: Done with file read");
      pending = false;
      httpRequest waiter;
      
      while ((waiter = (httpRequest)waiting.remove_head()) != null) {
	httpResponder respd = new httpResponder(response, waiter);
	sendSink.enqueue_lossy(respd);
      }
    }
  }



}

