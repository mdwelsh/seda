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
import seda.sandStorm.lib.aDisk.AFileWriteRequest;

/**
 * Simple test program demonstrating use of AFile interface.
 * Copies the file given by the "infile" initarg to that
 * given by the "outfile" initarg.
 *
 * @author Matt Welsh
 */
public class CopyFile implements EventHandlerIF {

  private static final boolean DEBUG = false;

  private SinkIF mysink;
  private ssTimer timer;
  private String INFILE, OUTFILE;
  private AFile inaf, outaf;

  public void init(ConfigDataIF config) throws Exception {
    mysink = config.getStage().getSink();

    INFILE = config.getString("infile");
    OUTFILE = config.getString("outfile");

    System.err.println("Started");

    inaf = new AFile(INFILE, mysink, false, true);
    outaf = new AFile(OUTFILE, mysink, true, false);

    long size = inaf.stat().length;
    System.err.println("Size is "+size);
    BufferElement buf = new BufferElement((int)size);
    inaf.read(buf);
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("GOT QEL: "+item);

    if (item instanceof AFileIOCompleted) {
      AFileIOCompleted ioc = (AFileIOCompleted)item;

      if (ioc.getRequest() instanceof AFileReadRequest) {
	BufferElement buf = ((AFileReadRequest)ioc.getRequest()).getBuffer();
	try {
  	  outaf.write(((AFileReadRequest)ioc.getRequest()).getBuffer());
	  outaf.flush();
	  outaf.close();
	} catch (SinkException se) {
	  System.err.println("Got SE: "+se);
	}
      } else if (ioc.getRequest() instanceof AFileWriteRequest) {
	System.err.println("WRITE COMPLETE");
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

