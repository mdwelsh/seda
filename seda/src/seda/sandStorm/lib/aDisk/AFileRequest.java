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

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkIF;

/**
 * Abstract base class of I/O requests which can be posted to the
 * AFile enqueue() methods.
 *
 * @author Matt Welsh
 * @see AFileReadRequest
 * @see AFileWriteRequest
 * @see AFileSeekRequest
 * @see AFileFlushRequest
 * @see AFileCloseRequest
 */
public abstract class AFileRequest implements QueueElementIF {

  AFile afile;
  SinkIF compQ;

  protected AFileRequest(SinkIF compQ) {
    this.compQ = compQ;
  }

  protected AFileRequest(AFile afile, SinkIF compQ) {
    this.afile = afile;
    this.compQ = compQ;
  }

  public AFile getFile() {
    return afile;
  }

  AFileImpl getImpl() {
    return afile.getImpl();
  }

  SinkIF getCompQ() {
    return compQ;
  }

  void complete(QueueElementIF comp) {
    if (compQ != null) compQ.enqueue_lossy(comp);
  }

}


