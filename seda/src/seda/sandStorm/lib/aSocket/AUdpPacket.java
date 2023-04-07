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

package seda.sandStorm.lib.aSocket;

import seda.sandStorm.api.SinkIF;
import seda.sandStorm.core.BufferElement;

import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * An AUdpPacket is an extension to BufferElement that supports
 * specifying the destination address and port for a given packet.
 *
 * @author Matt Welsh 
 */
public class AUdpPacket extends BufferElement {

  public InetAddress address = null;
  public int port = -1;


  /**
   * Create an AUdpPacket with the given data, an offset of 0, and a 
   * size of data.length.
   */
  public AUdpPacket(byte data[]) {
    this(data, 0, data.length, null);
  }

  /**
   * Create an AUdpPacket with the given data, an offset of 0, and a 
   * size of data.length, with the given completion queue.
   */
  public AUdpPacket(byte data[], SinkIF compQ) {
    this(data, 0, data.length, compQ);
  }

  /**
   * Create an AUdpPacket with the given data, offset, and size.
   */
  public AUdpPacket(byte data[], int offset, int size) {
    this(data, offset, size, null);
  }

  /**
   * Create an AUdpPacket with the given data, offset, size, and 
   * completion queue.
   */
  public AUdpPacket(byte data[], int offset, int size, SinkIF compQ) {
    super(data, offset, size, compQ);
  }

  /**
   * Create an AUdpPacket with the given data, offset, size, 
   * completion queue, destination address, and port.
   */
  public AUdpPacket(byte data[], int offset, int size, SinkIF compQ, InetAddress address, int port) {
    super(data, offset, size, compQ);
    this.address = address;
    this.port = port;
  }

  /**
   * Create an AUdpPacket with a new data array of the given size.
   */
  public AUdpPacket(int size) {
    this(new byte[size], 0, size, null);
  }

  /**
   * Create an AUdpPacket from the given DatagramPacket and a null
   * completion queue.
   */
  public AUdpPacket(DatagramPacket dp) {
    this(dp.getData(), dp.getOffset(), dp.getLength(), null, dp.getAddress(), dp.getPort());
  }

  /**
   * Create an AUdpPacket from the given DatagramPacket and completion
   * queue.
   */
  public AUdpPacket(DatagramPacket dp, SinkIF compQ) {
    this(dp.getData(), dp.getOffset(), dp.getLength(), compQ, dp.getAddress(), dp.getPort());
  }

  /**
   * Return the destination address. Returns null if not set.
   */
  public InetAddress getAddress() {
    return address;
  }

  /**
   * Return the destination port. Returns -1 if not set.
   */
  public int getPort() {
    return port;
  }


}

