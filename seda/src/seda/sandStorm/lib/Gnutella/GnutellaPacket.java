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

package seda.sandStorm.lib.Gnutella;

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.core.BufferElement;

/** 
 * This is the base class for all Gnutella network packets.
 */
public abstract class GnutellaPacket implements GnutellaConst, QueueElementIF {

  private static final boolean DEBUG = false;

  public static int NUM_ALLOC = 0;

  protected GnutellaConnection gc;
  protected GnutellaGUID guid;
  protected int function;
  // These are public so they can be modified by user routing code
  public int ttl;
  public int hops;
  protected int payload_length;
  protected byte payload[];

  public void finalize() {
    NUM_ALLOC--;
  }

  protected GnutellaPacket(GnutellaGUID guid, int function, int ttl, int hops, byte payload[]) {
    NUM_ALLOC++;

    this.guid = guid;
    this.function = function;
    this.ttl = ttl;
    this.hops = hops;
    this.payload = payload;
  }

  protected GnutellaPacket(int function, byte payload[]) {
    this(new GnutellaGUID(), function, DEFAULT_TTL, DEFAULT_HOPS, payload);
  }

  protected GnutellaPacket(GnutellaGUID guid, int function, byte payload[]) {
    this(guid, function, DEFAULT_TTL, DEFAULT_HOPS, payload);
  }

  /**
   * Used by GnutellaServer when creating a new packet.
   */
  void setConnection(GnutellaConnection gc) {
    this.gc = gc;
  }

  /** 
   * Return the GnutellaConnection from which this packet arrived. 
   * In order to send a reply to this packet, you can call
   * 'packet.send(origPacket.getConnection()'.
   */
  public GnutellaConnection getConnection() {
    return gc;
  }

  /**
   * Return the GUID associated with this packet.
   */
  public GnutellaGUID getGUID() {
    return guid;
  }

  /** 
   * Implemented by subclasses to prepare for sending
   */
  protected void prepareForSend() {
    // Do nothing in default case
  }

  /**
   * Render the packet as a BufferElement which can be pushed to an
   * aSocket connection.
   */
  BufferElement getBuffer() {
    if (DEBUG) System.err.println("GnutellaPacket: doing prepareForSend");
    prepareForSend();
    if (payload == null) 
      payload_length = 0;
    else 
      payload_length = payload.length;

    byte data[] = new byte[PACKET_HEADER_SIZE+payload_length];
    guid.dump(data, 0);
    data[16] = (byte)(function & 0xff);
    data[17] = (byte)(ttl & 0xff);
    data[18] = (byte)(hops & 0xff);
    writeLEInt(payload_length, data, 19);
    if (payload != null) 
      System.arraycopy(payload, 0, data, PACKET_HEADER_SIZE, payload_length);
    BufferElement buf = new BufferElement(data);
    return buf;
  }

  // Return size of packet in bytes
  public int getSize() {
    prepareForSend();
    if (payload == null) 
      payload_length = 0;
    else 
      payload_length = payload.length;
    return PACKET_HEADER_SIZE+payload_length;
  }

  protected static void writeLEInt(int i, byte barr[], int offset) {
    barr[offset] = (byte)(i & 0xff);
    barr[offset+1] = (byte)((i & 0xff00) >> 8);
    barr[offset+2] = (byte)((i & 0xff0000) >> 16);
    barr[offset+3] = (byte)((i & 0xff000000) >> 24);
  }

  protected static int readLEInt(byte barr[], int offset) {
    int tmp = (((int)barr[offset+0]) & 0xff) |
              (((int)barr[offset+1] << 8) & 0xff00) |
              (((int)barr[offset+2] << 16) & 0xff0000) |
              (((int)barr[offset+3] << 24) & 0xff000000);
    return tmp;
  }

  protected static void writeLEShort(int i, byte barr[], int offset) {
    barr[offset] = (byte)(i & 0xff);
    barr[offset+1] = (byte)((i & 0xff00) >> 8);
  }

  protected static int readLEShort(byte barr[], int offset) {
    int tmp = (((int)barr[offset+0]) & 0xff) |
              (((int)barr[offset+1] << 8) & 0xff00);
    return tmp;
  }

  public String toString() {
    return "GnutellaPacket (generic)";
  }


}
