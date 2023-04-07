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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A Gnutella query hits packet.
 */
public class GnutellaQueryHitsPacket extends GnutellaPacket {

  private static final boolean DEBUG = false;

  int port;
  InetAddress address;
  private static InetAddress localAddress;
  int speed;
  private GnutellaQueryHit hits[];
  private GnutellaGUID hitsGUID;

  static {
    try {
      localAddress = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      System.err.println("GnutellaQueryHitsPacket: WARNING: Cannot obtain local IP address: "+e.getMessage());
      localAddress = null;
    }
  }

  /**
   * Create a GnutellaQueryHitsPacket from the given payload.
   */
  public GnutellaQueryHitsPacket(byte[] payload) throws UnknownHostException {
    super(GNUTELLA_FN_QUERYHITS, payload);
    //parsePayload();
  }

  /**
   * Create a GnutellaQueryHitsPacket from the given payload with the
   * given GUID, ttl, and hops.
   */
  public GnutellaQueryHitsPacket(GnutellaGUID guid, int ttl, int hops, byte[] payload) throws UnknownHostException {
    super(guid, GNUTELLA_FN_QUERYHITS, ttl, hops, payload);
    //parsePayload();
  }

  /**
   * Create a GnutellaQueryHitsPacket from the given GnutellaQueryHit
   * array.
   */
  public GnutellaQueryHitsPacket(GnutellaQueryHit hits[]) {
    super(GNUTELLA_FN_QUERYHITS, null);
    this.address = localAddress;
    this.port = DEFAULT_DOWNLOAD_PORT;
    this.speed = DEFAULT_SPEED;
    this.hits = hits;
    this.hitsGUID = new GnutellaGUID();
  }

  /**
   * Create a GnutellaQueryHitsPacket with a single hit with the given
   * index, size, and filename.
   */
  public GnutellaQueryHitsPacket(int index, int size, String filename) {
    this((GnutellaQueryHit[])null);
    //this.hits = new GnutellaQueryHit[1];
    //this.hits[0] = new GnutellaQueryHit(index, size, filename);
  }

  private void parsePayload() throws UnknownHostException {
    int num_hits = payload[0];
    port = readLEShort(payload, 1);

    String addr = (int)(payload[2] & 0xff) +"."+ (int)(payload[3] & 0xff) +"."+ (int)(payload[4] & 0xff) +"."+ (int)(payload[5] & 0xff);
    address = InetAddress.getByName(addr);

    speed = readLEInt(payload, 7);
    int off = 8;

    hits = new GnutellaQueryHit[num_hits];
    for (int i = 0; i < num_hits; i++) {
      hits[i] = new GnutellaQueryHit(payload, off);
      off += hits[i].getSize();
    }

    hitsGUID = new GnutellaGUID(payload, off);
  }

  protected void prepareForSend() {

    //int sz = 11+16; // header plus hitsGUID

    //int hitssz = 0;
    //for (int i = 0; i < hits.length; i++) {
    //  hitssz += hits[i].getSize();
    //}
    //payload = new byte[sz+hitssz];

    //payload[0] = (byte)hits.length;
    //writeLEShort(port, payload, 1);
    //byte addr[] = address.getAddress();
    //payload[3] = addr[0];
    //payload[4] = addr[1];
    //payload[5] = addr[2];
    //payload[6] = addr[3];
    //writeLEInt(speed, payload, 7);

    //int off = 11;
    //for (int i = 0; i < hits.length; i++) {
    //  hits[i].dump(payload, off);
    //  off += hits[i].getSize();
    //}
    //hitsGUID.dump(payload, off);
  }


  public String toString() {
    return "GnutellaQueryHitsPacket";
  }


}

