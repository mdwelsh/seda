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
 * A Gnutella network pong packet. 
 */
public class GnutellaPongPacket extends GnutellaPacket {

  private static final boolean DEBUG = false;

  private int port;
  private InetAddress address;
  private int numfiles;
  private int numkb;

  /**
   * Create a pong packet with the given payload.
   */
  public GnutellaPongPacket(byte[] payload) throws UnknownHostException {
    super(GNUTELLA_FN_PONG, payload);
    if (payload != null) parsePayload();
  }

  /**
   * Create a pong packet with the given GUID, TTL, hops, and payload.
   */
  public GnutellaPongPacket(GnutellaGUID guid, int ttl, int hops, byte[] payload) throws UnknownHostException {
    super(guid, GNUTELLA_FN_PONG, ttl, hops, payload);
    if (payload != null) parsePayload();
  }

  /**
   * Create a pong packet with the given numfiles and numkb, with the
   * default port and local host address.
   */
  public GnutellaPongPacket(GnutellaGUID guid, int numfiles, int numkb) throws UnknownHostException {
    this(guid, DEFAULT_GNUTELLA_PORT, InetAddress.getLocalHost(), numfiles, numkb);
  }

  /**
   * Create a pong packet with the given port, address, numfiles and numkb.
   */
  public GnutellaPongPacket(GnutellaGUID guid, int port, InetAddress address, int numfiles, int numkb) throws UnknownHostException {
    super(guid, GNUTELLA_FN_PONG, null);
    this.port = port;
    this.address = address;
    this.numfiles = numfiles;
    this.numkb = numkb;
  }

  public String toString() {
    return "GnutellaPongPacket "+guid.toString()+" ["+address.getHostAddress()+":"+port+" - "+numfiles+" files/"+numkb+" KB]";
  }

  protected void prepareForSend() {
    payload = new byte[14];

    writeLEShort(((short)port & 0xffff), payload, 0);
    byte addr[] = address.getAddress();
    payload[2] = addr[0];
    payload[3] = addr[1];
    payload[4] = addr[2];
    payload[5] = addr[3];

    writeLEInt(numfiles, payload, 6);
    writeLEInt(numkb, payload, 10);
  }

  private void parsePayload() throws UnknownHostException {
    port = (int)readLEShort(payload, 0);
    String addr = (int)(payload[2] & 0xff) +"."+ (int)(payload[3] & 0xff) +"."+ (int)(payload[4] & 0xff) +"."+ (int)(payload[5] & 0xff);
    address = InetAddress.getByName(addr);

    numfiles = readLEInt(payload, 6);
    numkb = readLEInt(payload, 10);
  }

  /**
   * Return the address represented by this packet.
   */
  public InetAddress getInetAddress() {
    return address;
  }

  /**
   * Return the port represented by this packet.
   */
  public int getPort() {
    return port;
  }

  /**
   * Return a string "host:port" represented by this packet.
   */
  public String getHost() {
    return address.getHostAddress()+":"+port;
  }

  /**
   * Return the number of files shared by the machine from which this
   * packet originated.
   */
  public int getNumFiles() {
    return numfiles;
  }

  /** 
   * Return the number of kilobytes of files shared by the machine from
   * which this packet originated.
   */
  public int getNumKB() {
    return numkb;
  }

}

