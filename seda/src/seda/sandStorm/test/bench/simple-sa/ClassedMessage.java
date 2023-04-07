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

import seda.sandStorm.api.ClassQueueElementIF;
import seda.sandStorm.api.SinkIF;

public class ClassedMessage extends Message implements ClassQueueElementIF, SimpleP2PConst {

  private static final boolean DEBUG = false;
  public int theclass;

  public ClassedMessage(int seqNum, int status, int theclass, SinkIF sendQ, SinkIF replyQ) {
    super(seqNum, status, sendQ, replyQ);
    this.theclass = theclass;
  }

  public int getRequestClass() {
    return this.theclass;
  }

  public void setRequestClass(int theclass) {
    this.theclass = theclass;
  }

  public int hashCode() {
    return seqNum;
  }

  public boolean equals(Object o) {
    if (o instanceof ClassedMessage) {
      ClassedMessage cm = (ClassedMessage)o;
      if (cm.seqNum == this.seqNum) return true;
    }
    return false;
  }

  public String toString() {
    return "[ClassedMessage seqNum="+seqNum+" status="+status+" class="+theclass+"]";
  }

}
