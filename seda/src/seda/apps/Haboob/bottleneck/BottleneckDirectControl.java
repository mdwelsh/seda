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

package seda.apps.Haboob.bottleneck;

import seda.apps.Haboob.HaboobConst;
import seda.apps.Haboob.HaboobStats;
import seda.apps.Haboob.http.HttpSend;
import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.core.QueueThresholdPredicate;
import seda.sandStorm.lib.http.httpBadRequestResponse;
import seda.sandStorm.lib.http.httpOKResponse;
import seda.sandStorm.lib.http.httpRequest;
import seda.sandStorm.lib.http.httpResponder;
import seda.util.MDWUtil;

import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Random;

/**
 * An intentional bottleneck stage, used for demonstrating load conditioning.
 * Does some I/O and CPU crunching to generate a dynamic web page; also
 * provides an adaptive load shedding controller that adjusts the stage's
 * queue threshold to meet a response time target. All of this is described
 * in the SOSP'01 paper on SEDA, found at
 *   http://www.cs.berkeley.edu/~mdw/proj/seda/
 *
 * This version implements its own threshold-based response time controller.
 * 
 */
public class BottleneckDirectControl implements EventHandlerIF, HaboobConst {

  private static final boolean DEBUG = false;
  private static final boolean VERBOSE = false;

  private static final int OUTPUT_STATIC_PAGE_SIZE = 8192;
  
  private SinkIF sendSink;
  private Hashtable ht;
  private Random rand;
  private httpOKResponse static_page_response;

  // If true, allocate a large byte array and insert into a hashtable
  private static final boolean BOTTLENECK_ALLOC = false;
  private static final int MAX_ALLOC_SIZE = 81920;

  // If true, sleep
  private static final boolean BOTTLENECK_SLEEP = false;
  private static final long SLEEP_TIME = 100;

  // If true, read data from file and process sums
  private static final boolean BOTTLENECK_PROCESSFILE = true;
  // If true, generate random data and process sums.
  private static final boolean BOTTLENECK_PROCESSRANDOM = false;
  private static final String RANDOM_FILE = "/scratch/mdw/specweb99-runs/cgi-bin/random.data";
  private static final int NUM_RUNS = 50;
  private static final int NUM_SUMS = 50;
  private static final int NUM_BYTES_TO_READ = 100;
  private volatile static int sum = 0;
  private static byte data[] = new byte[NUM_BYTES_TO_READ];

  // Adjust queue threshold to meet a response time target
  private double targetResponseTime = -1.0;
  private double smoothConst = -1.0;
  private double currentResponseTime;
  private FiniteQueue myqueue;
  private int curThreshold;
  private int recalcCount = 0;
  private static final int RECALC_WINDOW = 200;
  private static final int MAX_THRESHOLD = 1024;
  private static final int MIN_THRESHOLD = 1;

  public void init(ConfigDataIF config) throws Exception {
    SinkIF mysink = config.getStage().getSink();

    // XXX Kind of a hack -- get a handle to our own finitequeue
    try {
      myqueue = (FiniteQueue)mysink;
    } catch (ClassCastException cce) {
      // Whoops - try to get a SinkProxy instead
      myqueue = (FiniteQueue)((seda.sandStorm.internal.SinkProxy)mysink).thesink;
    }

    // Add profile 
    ManagerIF mgr = config.getManager();
    mgr.getProfiler().add("Bottleneck stage response time average",
	new ProfilableIF() {
	  public int profileSize() {
	    return (int)currentResponseTime;
	  }
	});
    mgr.getProfiler().add("Bottleneck stage queue threshold",
	new ProfilableIF() {
	  public int profileSize() {
	    return curThreshold;
	  }
	});

    sendSink = config.getManager().getStage(HTTP_SEND_STAGE).getSink();
    ht = new Hashtable();
    rand = new Random();
    targetResponseTime = config.getDouble("targetResponseTime");
    System.err.println("Bottleneck stage initialized, targetResponseTime "+MDWUtil.format(targetResponseTime)+" ms");
    if (targetResponseTime != -1.0) {
      smoothConst = config.getDouble("smoothConst");
      if (smoothConst == -1.0) smoothConst = 0.5;
      // Start out with min threshold
      curThreshold = MIN_THRESHOLD;
      ((QueueThresholdPredicate)myqueue.getEnqueuePredicate()).setThreshold(curThreshold);
    }

    byte response[] = new byte[OUTPUT_STATIC_PAGE_SIZE];
    for (int i = 0; i < OUTPUT_STATIC_PAGE_SIZE; i++) {
      response[i] = (byte)'A';
    }
    static_page_response = new httpOKResponse("text/plain", OUTPUT_STATIC_PAGE_SIZE);
    BufferElement payload = static_page_response.getPayload();
    byte paydata[] = payload.data;
    System.arraycopy(response, 0, paydata, payload.offset, payload.size);
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("Bottleneck: GOT QEL: "+item);

    if (item instanceof httpRequest) {
      HaboobStats.numRequests++;

      httpRequest req = (httpRequest)item;
      if (req.getRequest() != httpRequest.REQUEST_GET) {
	HaboobStats.numErrors++;
	sendSink.enqueue_lossy(new httpResponder(new httpBadRequestResponse(req, "Only GET requests supported at this time"), req, true));
	return;
      }

      // Do bottleneck work
      long t1, t2;
      if (VERBOSE) t1 = System.currentTimeMillis();
      doBottleneck();
      if (VERBOSE) { 
	t2 = System.currentTimeMillis();
	System.err.println("Bottleneck: "+(t2-t1)+" ms");
      }

      // Check response time
      if (targetResponseTime != -1.0) {
	synchronized (this) {
	  long cur = System.currentTimeMillis() - req.timestamp;
	  currentResponseTime = (smoothConst * currentResponseTime) + ((1.0 - smoothConst) * (cur * 1.0));
	  recalcCount++;
	  if (recalcCount == RECALC_WINDOW) {
	    recalcCount = 0;
	    if (currentResponseTime < (0.9 * targetResponseTime)) {
	      curThreshold += 2;
	      if (curThreshold > MAX_THRESHOLD) curThreshold = MAX_THRESHOLD;
	    } else if (currentResponseTime > (1.1 * targetResponseTime)) {
	      curThreshold /= 2;
	      if (curThreshold < MIN_THRESHOLD) curThreshold = MIN_THRESHOLD;
	    }
	    System.err.println("Bottleneck: target "+MDWUtil.format(targetResponseTime)+", current "+MDWUtil.format(currentResponseTime)+", threshold "+curThreshold);
	    ((QueueThresholdPredicate)myqueue.getEnqueuePredicate()).setThreshold(curThreshold);
	  }
	}
      }

      // Send response 
      httpResponder resp = new httpResponder(static_page_response, req, false);
      HttpSend.sendResponse(resp);
      return;

    } else if (item instanceof SinkClosedEvent) {
      // Ignore

    } else {
      System.err.println("StaticPage: Got unknown event type: "+item);
    }

  }

  public void handleEvents(QueueElementIF items[]) {
    if (DEBUG) System.err.println("Bottleneck: "+Thread.currentThread()+" got "+items.length+" events");

    for(int i=0; i<items.length; i++) {
      handleEvent(items[i]);
    }
  }

  private void doBottleneck() {

    if (BOTTLENECK_ALLOC) {
      // Allocate big chunk of memory and stash it away
      int sz = Math.abs(rand.nextInt()) % MAX_ALLOC_SIZE;
      int key = Math.abs(rand.nextInt());
      ht.put(new Integer(key), new byte[sz]);
    } 

    if (BOTTLENECK_SLEEP) {
      MDWUtil.sleep(SLEEP_TIME);
    }

    if (BOTTLENECK_PROCESSFILE) {
      try {

	for (int run = 0; run < NUM_RUNS; run++) { 
	  RandomAccessFile raf = new RandomAccessFile(RANDOM_FILE, "r");
	  for (int i = 0; i < NUM_BYTES_TO_READ; i++) {
	    raf.read(data, 0, NUM_BYTES_TO_READ);
//	    data[i] = (byte)raf.read();
	  }
	  raf.close();
	  for (int n = 0; n < NUM_SUMS; n++) {
	    for (int i = 0; i < NUM_BYTES_TO_READ; i++) {
	      sum += data[i];
	    }
	  }
	}

      } catch (Exception e) {
	System.err.println("Warning: Bottleneck processing got exception: "+e);
      }
    }

    if (BOTTLENECK_PROCESSRANDOM) {
      try {
	Random r = new Random();
	for (int run = 0; run < NUM_RUNS; run++) { 
	  r.nextBytes(data);
	  for (int n = 0; n < NUM_SUMS; n++) {
	    for (int i = 0; i < NUM_BYTES_TO_READ; i++) {
	      sum += data[i];
	    }
	  }
	}

      } catch (Exception e) {
	System.err.println("Warning: Bottleneck processing got exception: "+e);
      }
    }

  }




}

