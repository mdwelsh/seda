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

import seda.sandStorm.api.ConfigDataIF;

import java.io.FileInputStream;
import java.util.Hashtable;

public class ProcessStageFileIO extends ProcessStage {

  private static final boolean DEBUG = false;

  private String FILENAME;
  private static final int BUFFER_SIZE = 8192; 
  private static final int NUM_READS = 1000;
  private static final Hashtable bufTbl = new Hashtable();

  public void init(ConfigDataIF config) throws Exception {
    super.init(config);

    FILENAME = config.getString("filename");
    if (FILENAME == null) {
      throw new Exception("Must specify filename");
    }
    System.err.println(config.getStage().getName()+": Started, filename="+FILENAME);
  }

  protected void processMessage(Message msg) {
    if (DEBUG) System.err.println("processMessage: Processing "+msg);


    try {
      byte barr[];
      synchronized (bufTbl) {
	barr = (byte[])bufTbl.get(Thread.currentThread());
	if (barr == null) {
	  barr = new byte[BUFFER_SIZE];
	  bufTbl.put(Thread.currentThread(), barr);
	}
      }

      FileInputStream fis = new FileInputStream(FILENAME);
      for (int i = 0; i < NUM_READS; i++) {
	while (fis.read(barr) >= 0) ;
	fis.reset();
      }
      fis.close();

    } catch (Exception e) {
      System.err.println("processMessage: Exception: "+e);
    }
  }
}

