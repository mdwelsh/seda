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

import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.ssTimer;
import seda.sandStorm.lib.aDisk.AFile;
import seda.sandStorm.lib.aDisk.AFileIOCompleted;
import seda.sandStorm.lib.aDisk.AFileReadRequest;

/**
 * Simple test program demonstrating use of the AFile interface.
 * Reads the file given in the "filename" initarg and prints is to
 * stderr in 100-byte chunks.
 *
 * @author Matt Welsh
 */
public class TestHandler implements EventHandlerIF {

  private static final boolean DEBUG = false;

  private SinkIF mysink;
  private ssTimer timer;
  private String FILENAME;
  private AFile af;

  public void init(ConfigDataIF config) throws Exception {
    mysink = config.getStage().getSink();

    if (config.getString("filename") == null) 
      System.err.println("Must specify filename!");

    FILENAME = config.getString("filename");

    System.err.println("Started");

    af = new AFile(FILENAME, mysink, false, true);
    BufferElement buf = new BufferElement(100);
    af.read(buf);
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("GOT QEL: "+item);

    if (item instanceof AFileIOCompleted) {
      AFileIOCompleted ioc = (AFileIOCompleted)item;
      BufferElement buf = ((AFileReadRequest)ioc.getRequest()).getBuffer();
      String s = new String(buf.data, buf.offset, ioc.sizeCompleted);
      System.err.print(s);

      try {
	af.read(((AFileReadRequest)ioc.getRequest()).getBuffer());
      } catch (SinkException se) {
	System.err.println("Got SE: "+se);
      }
    } else {
      if (DEBUG) System.err.println("Got unknown event type: "+item);
    }

  }

  public void handleEvents(QueueElementIF items[]) {
    if (DEBUG) System.err.println("GOT "+items.length+" ELEMENTS");
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

}

