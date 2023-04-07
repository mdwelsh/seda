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
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.lib.aSocket.ATcpConnection;

public class Message implements QueueElementIF, SimpleP2PConst {

  private static final boolean DEBUG = false;

  public static final int STATUS_OK = 0x00;
  public static final int STATUS_REJECTED = 0xff;

  public int seqNum;
  public int status;
  public ATcpConnection conn;

  public Message(int seqNum) {
    this.seqNum = seqNum;
    this.status = STATUS_OK;
    this.conn = null;
  }

  public Message(int seqNum, int status) {
    this.seqNum = seqNum;
    this.status = status;
    this.conn = null;
  }

  public Message(int seqNum, ATcpConnection conn) {
    this(seqNum);
    this.conn = conn;
  }

  public Message(int seqNum, int status, ATcpConnection conn) {
    this(seqNum, status);
    this.conn = conn;
  }

  public Message(byte data[]) {
    if (DEBUG) {
      System.err.print("Message created: ");
      for (int i = 0; i < data.length; i++) {
	System.err.print(data[i]+" ");
      }
      System.err.println("");
    }
    seqNum = (data[0] & 0xff) | 
      ((data[1] & 0xff) << 8) | 
      ((data[2] & 0xff) << 16) | 
      ((data[3] & 0xff) << 24);
    status = data[4];
    this.conn = null;
  }

  public Message(byte data[], ATcpConnection conn) {
    this(data);
    this.conn = conn;
  }

  public byte[] format() {
    byte data[] = new byte[MSG_SIZE];
    data[0] = (byte)(seqNum & 0xff);
    data[1] = (byte)((seqNum & 0xff00) >> 8);
    data[2] = (byte)((seqNum & 0xff0000) >> 16);
    data[3] = (byte)((seqNum & 0xff000000) >> 24);
    data[4] = (byte)(status & 0xff);
    if (DEBUG) {
      System.err.print("Message formatted: ");
      for (int i = 0; i < data.length; i++) {
	System.err.print(data[i]+" ");
      }
      System.err.println("");
    }
    return data;
  }

  public void send() {
    if (DEBUG) System.err.println("Message.send() called: "+toString());
    conn.enqueue_lossy(new BufferElement(format()));
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
