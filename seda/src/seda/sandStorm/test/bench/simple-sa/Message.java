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

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.core.TimeStampedEvent;

public class Message extends TimeStampedEvent implements QueueElementIF, SimpleP2PConst {

  private static final boolean DEBUG = false;

  public static final int STATUS_OK = 0x00;
  public static final int STATUS_REJECTED = 0xff;

  public int seqNum;
  public int status;
  public SinkIF compQ, replyQ;

  public Message(int seqNum, int status, SinkIF sendQ, SinkIF replyQ) {
    this.seqNum = seqNum;
    this.status = status;
    this.compQ = sendQ;
    this.replyQ = replyQ;
  }

  public void send() {
    if (DEBUG) System.err.println("Message.send() called: "+toString());
    this.timestamp = System.currentTimeMillis();
    compQ.enqueue_lossy(this);
  }

  public void reply() {
    if (DEBUG) System.err.println("Message.reply() called: "+toString());
    if (replyQ != null) replyQ.enqueue_lossy(this);
  }

  public int hashCode() {
    return seqNum;
  }

  public boolean equals(Object o) {
    if (o instanceof Message) {
      Message m = (Message)o;
      if (m.seqNum == this.seqNum) return true;
    }
    return false;
  }

  public String toString() {
    return "[Message seqNum="+seqNum+" status="+status+"]";
  }

}
