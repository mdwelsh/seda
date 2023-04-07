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

package seda.sandStorm.lib.aSocket;

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.core.BufferElement;

/** 
 * An ATcpInPacket represents a packet which was received from an
 * asynchronous socket. When a packet is received on a connection,
 * an ATcpInPacket is pushed to the SinkIF associated with an
 * ATcpConnection.
 *
 * @author Matt Welsh
 * @see ATcpConnection
 */
public class ATcpInPacket implements QueueElementIF {

  private ATcpConnection conn;
  private BufferElement buf;
  // package access
  long seqNum;

  public ATcpInPacket(ATcpConnection conn, BufferElement buf) {
    this.conn = conn;
    this.buf = buf;
    this.seqNum = 0;
  }

  public ATcpInPacket(ATcpConnection conn, BufferElement buf, long seqNum) {
    this.conn = conn;
    this.buf = buf;
    this.seqNum = seqNum;
  }

  public ATcpInPacket(ATcpConnection conn, byte data[], int len) {
    this.conn = conn;
    byte newdata[] = new byte[len];
    System.arraycopy(data, 0, newdata, 0, len);
    this.buf = new BufferElement(newdata);
    this.seqNum = 0;
  }

  public ATcpInPacket(ATcpConnection conn, byte data[], int len, long seqNum) {
    this.conn = conn;
    byte newdata[] = new byte[len];
    System.arraycopy(data, 0, newdata, 0, len);
    this.buf = new BufferElement(newdata);
    this.seqNum = seqNum;
  }

  public ATcpInPacket(ATcpConnection conn, byte data[], int len, boolean copy) {
    this.conn = conn;
    if (copy) {
      byte newdata[] = new byte[len];
      System.arraycopy(data, 0, newdata, 0, len);
      this.buf = new BufferElement(newdata);
    } else {
      this.buf = new BufferElement(data, 0, len);
    }
    this.seqNum = 0;
  }

  public ATcpInPacket(ATcpConnection conn, byte data[], int len, boolean copy, long seqNum) {
    this.conn = conn;
    if (copy) {
      byte newdata[] = new byte[len];
      System.arraycopy(data, 0, newdata, 0, len);
      this.buf = new BufferElement(newdata);
    } else {
      this.buf = new BufferElement(data, 0, len);
    }
    this.seqNum = seqNum;
  }

  /**
   * Return the connection from which this packet was received.
   */
  public ATcpConnection getConnection() {
    return conn;
  }

  /** 
   * Return the data from an incoming TCP packet.
   */
  public byte[] getBytes() {
    return buf.data;
  }

  /**
   * Return the size of the packet data.
   */
  public int size() {
    return buf.size;
  }

  /**
   * Return the BufferElement associated with the packet data.
   */
  public BufferElement getBufferElement() {
    return buf;
  }

  /**
   * Return the sequence number associated with this packet.
   * Sequence numbers range from 1 to Long.MAX_VALUE, then wrap
   * around to Long.MIN_VALUE. A sequence number of 0 indicates that
   * no sequence number was associated with this packet when it was
   * created.
   */
  public long getSequenceNumber() {
    return seqNum;
  }

  public String toString() {
    return "ATcpInPacket [conn="+conn+", size="+buf.size+"]";
  }


}
