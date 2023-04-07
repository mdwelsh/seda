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

import java.net.DatagramPacket;

/** 
 * An AUdpInPacket represents a packet which was received from a
 * datagram socket. 
 *
 * @author Matt Welsh
 */
public class AUdpInPacket implements QueueElementIF {

  private AUdpSocket sock;
  private DatagramPacket packet;
  // package access
  long seqNum;

  public AUdpInPacket(AUdpSocket sock, DatagramPacket packet) {
    this.sock = sock;
    this.packet = packet;
    this.seqNum = 0;
  }

  public AUdpInPacket(AUdpSocket sock, DatagramPacket packet, long seqNum) {
    this.sock = sock;
    this.packet = packet;
    this.seqNum = seqNum;
  }

  /**
   * Return the socket from which this packet was received.
   */
  public AUdpSocket getSocket() {
    return sock;
  }

  /** 
   * Return the DatagramPacket.
   */
  public DatagramPacket getPacket() {
    return packet;
  }

  /**
   * Return the packet data.
   */
  public byte[] getBytes() {
    return packet.getData();
  }

  /**
   * Return the size of the packet data.
   */
  public int size() {
    return packet.getLength();
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
    return "AUdpInPacket [sock="+sock+", size="+packet.getLength()+"]";
  }


}
