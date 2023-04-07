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

import java.io.PrintStream;

/**
 * A Gnutella query packet.
 */
public class GnutellaQueryPacket extends GnutellaPacket {

  private static final boolean DEBUG = false;

  int minspeed;
  String searchterm;

  /** 
   * Create a query packet with the given payload. The payload should
   * conform to the format specified by the Gnutella protocol.
   */
  public GnutellaQueryPacket(byte[] payload) {
    super(GNUTELLA_FN_QUERY, payload);
    parsePayload();
  }

  /**
   * Create a query packet with the given GUID, TTL, hops, and payload.
   */
  public GnutellaQueryPacket(GnutellaGUID guid, int ttl, int hops, byte[] payload) {
    super(guid, GNUTELLA_FN_QUERY, ttl, hops, payload);
    parsePayload();
  }

  /** 
   * Create a query packet with the given search term and minspeed.
   */
  public GnutellaQueryPacket(String searchterm, int minspeed) {
    super(GNUTELLA_FN_QUERY, null);
    this.minspeed = minspeed;
    this.searchterm = searchterm;
  }

  private void parsePayload() {
    if ((payload == null) ||
	(payload.length < 3)) {
      // Technically this doesn't make sense, but lots of bogus packets
      // are flying around out there
      return;
    }

    minspeed = readLEShort(payload, 0);
    // Ignore null byte at end
    // Strip off non-ASCII characters
    if (payload.length > 3) {
      for (int i = 2; i < payload.length-3; i++) {
        if ((payload[i] < 32) || (payload[i] > 126)) payload[i] = (byte)'?';
      }
      searchterm = new String(payload, 2, payload.length-3);
    } else {
      searchterm = null;
    }
  }

  protected void prepareForSend() {
    if (searchterm != null) {
      byte barr[] = searchterm.getBytes();
    // Extra null at end
      int payload_len = 2+barr.length+1;
      payload = new byte[payload_len];
      writeLEShort((short)minspeed & 0xffff, payload, 0);
      System.arraycopy(barr, 0, payload, 2, barr.length);
      payload[payload_len-1] = 0;
    } else {
      // Just null char for search term
      payload = new byte[3];
      writeLEShort((short)minspeed & 0xffff, payload, 0);
      payload[2] = 0;
    }
  }

  public String toString() {
    return "GnutellaQueryPacket "+guid+" ["+searchterm+"]";
  }

  public void debug(PrintStream out) {
    out.println("GnutellaQueryPacket: "+searchterm+" (min "+minspeed+" KB/sec)");
  }

  /** 
   * Return the search term contained in this query packet.
   */
  public String getSearchTerm() {
    return searchterm;
  } 

  /** 
   * Return the minimum speed requested by this query packet.
   */
  public int getMinSpeed() {
    return minspeed;
  }

}

