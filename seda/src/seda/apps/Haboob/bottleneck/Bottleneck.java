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
import seda.sandStorm.api.internal.ResponseTimeControllerIF;
import seda.sandStorm.api.internal.StageStatsIF;
import seda.sandStorm.core.BufferElement;
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
 * This version relies upon the system-supplied response time controller.
 * 
 */
public class Bottleneck implements EventHandlerIF, HaboobConst {

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

  // If true, degrade quality of responses to meet RT target
  private static final boolean BOTTLENECK_DEGRADE = false;
  private static final boolean BOTTLENECK_DEGRADE_RTCON = false;
  private static final int BOTTLENECK_DEGRADE_RTCON_THRESH = 10;
  private static double MIN_QUALITY = 0.01;
  private static double MAX_QUALITY = 1.0;
  private static double RTCON_DISABLE_QUALITY = 0.2;
  private static double quality = MAX_QUALITY;
  private static double VERY_HIGH_WATER = 1.5;
  private static double HIGH_WATER = 1.1;
  private static double LOW_WATER = 0.8;
  private static double MEDIUM_WATER = 0.5;
  private static double VERY_LOW_WATER = 0.0001;
  private static double BOTTLENECK_DEGRADE_ADDITIVE_INCREASE = 0.01;
  private static double BOTTLENECK_DEGRADE_MULTIPLICATIVE_DECREASE = 2.0;
  private static double BOTTLENECK_DEGRADE_COUNT = 100;

  private StageStatsIF stats;
  private ResponseTimeControllerIF rtcon;
  private double targetRT;
  private int degrade_count = 0;
  private int degrade_min_count = 0;

  public void init(ConfigDataIF config) throws Exception {
    SinkIF mysink = config.getStage().getSink();

    stats = config.getStage().getWrapper().getStats();
    rtcon = config.getStage().getWrapper().getResponseTimeController();
    if (rtcon != null) {
      System.err.println("** BOTTLENECK: SETTING TARGET");
      targetRT = rtcon.getTarget();
      rtcon.disable();
    }

    sendSink = config.getManager().getStage(HTTP_SEND_STAGE).getSink();
    ht = new Hashtable();
    rand = new Random();
    System.err.println("Bottleneck stage initialized, BOTTLENECK_DEGRADE="+BOTTLENECK_DEGRADE+", BOTTLENECK_DEGRADE_RTCON="+BOTTLENECK_DEGRADE_RTCON);

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

      double ninetiethRT = stats.get90thRT();
      if (BOTTLENECK_DEGRADE && (++degrade_count >= BOTTLENECK_DEGRADE_COUNT)) {
	degrade_count = 0;

	if (ninetiethRT < (VERY_LOW_WATER * targetRT)) {

	  degrade_min_count = 0;
	  quality *= BOTTLENECK_DEGRADE_MULTIPLICATIVE_DECREASE;
	  if (quality > MAX_QUALITY) quality = MAX_QUALITY;
	  if (BOTTLENECK_DEGRADE_RTCON) {
	    rtcon.disable();
	  }

	} else if (ninetiethRT < (LOW_WATER * targetRT)) {

	  degrade_min_count = 0;
	  quality += BOTTLENECK_DEGRADE_ADDITIVE_INCREASE;
	  if (quality > MAX_QUALITY) quality = MAX_QUALITY;
	  if (BOTTLENECK_DEGRADE_RTCON && quality >= RTCON_DISABLE_QUALITY) {
	    rtcon.disable();
	  }
	  //if (BOTTLENECK_DEGRADE_RTCON) {
	  //  rtcon.disable();
	  //}

	} else if (ninetiethRT < (MEDIUM_WATER * targetRT)) {

	  degrade_min_count = 0;
	  if (BOTTLENECK_DEGRADE_RTCON) {
	    rtcon.disable();
	  }

	} else if (ninetiethRT > (VERY_HIGH_WATER * targetRT)) {

	  if (BOTTLENECK_DEGRADE_RTCON) {
	    rtcon.enable();
	  }

	  quality /= BOTTLENECK_DEGRADE_MULTIPLICATIVE_DECREASE;
	  if (quality <= MIN_QUALITY) {
	    quality = MIN_QUALITY;
	  }

	} else if (ninetiethRT > (HIGH_WATER * targetRT)) {

	  quality /= BOTTLENECK_DEGRADE_MULTIPLICATIVE_DECREASE;
	  if (quality <= MIN_QUALITY) {
	    quality = MIN_QUALITY;

	    if (BOTTLENECK_DEGRADE_RTCON) {
	      degrade_min_count++;
	      if (degrade_min_count >= BOTTLENECK_DEGRADE_RTCON_THRESH) {
		rtcon.enable();
	      }
	    }
	  }
	}
	System.err.println("Bottleneck: 90th RT "+ninetiethRT+", target "+targetRT+", quality now "+quality);
      }

      // Send response 
      String respstr = "BOTTLENECK: 90thRT "+ninetiethRT+" targetRT "+targetRT+" quality "+quality+"\n\n";
      byte respstrbytes[] = respstr.getBytes();
      httpOKResponse resp = new httpOKResponse("text/plain", OUTPUT_STATIC_PAGE_SIZE);
      BufferElement payload = resp.getPayload();
      byte paydata[] = payload.data;
      System.arraycopy(respstrbytes, 0, paydata, payload.offset, respstrbytes.length);
      httpResponder respd = new httpResponder(resp, req, false);
      HttpSend.sendResponse(respd);
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

	for (int run = 0; run < (NUM_RUNS * quality); run++) { 
	  RandomAccessFile raf = new RandomAccessFile(RANDOM_FILE, "r");
	  for (int i = 0; i < NUM_BYTES_TO_READ; i++) {
	    raf.read(data, 0, NUM_BYTES_TO_READ);
//	    data[i] = (byte)raf.read();
	  }
	  raf.close();
	  for (int n = 0; n < (NUM_SUMS * quality); n++) {
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

