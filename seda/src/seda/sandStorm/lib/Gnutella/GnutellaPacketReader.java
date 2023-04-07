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

import seda.sandStorm.lib.aSocket.ATcpInPacket;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * This is an internal class, responsible for generating GnutellaPacket
 * objects from raw socket data. It encapsulates the core packet-processing
 * code in the Gnutella protocol.
 *
 * @author Matt Welsh
 */
class GnutellaPacketReader implements GnutellaConst {

  private static final boolean DEBUG = false;

  private static final int STATE_READ_HEADER = 0;
  private static final int STATE_READ_PAYLOAD = 1;
  private int state;

  private int cur_offset, packet_offset;
  private byte pktdata[];

  private GnutellaGUID guid;
  private int function;
  private int ttl;
  private int hops;
  private int payload_length;

  private byte header[];
  private byte payload[];

  private Vector completePackets;

  GnutellaPacketReader() {
    state = STATE_READ_HEADER;
    header = new byte[PACKET_HEADER_SIZE];
    cur_offset = 0;
    completePackets = new Vector(1);
  }

  void pushPacket(ATcpInPacket pkt) throws IOException {

    packet_offset = 0;
    pktdata = pkt.getBytes();

    if (DEBUG) System.err.println("GPR: pushPacket called, size "+pktdata.length);

    boolean proceed = true;

    try {
      while (proceed) {
	switch (state) {
	  case STATE_READ_HEADER: 
	    proceed = doReadHeader();
	    break;

	  case STATE_READ_PAYLOAD:
	    proceed = doReadPayload();
	    break;

	  default:
	    throw new RuntimeException("Bad state in GnutellaPacketReader - this is a bug, please contact <mdw@cs.berkeley.edu>");
	}
      }
    } catch (IOException e) {
      // If an error occurs during processing, just drop everything
      // and wait for the next packet
      reset();
      throw e;
    }
  }

  // Used to reset after an error
  private void reset() {
    cur_offset = 0;
    state = STATE_READ_HEADER;
  }

  private boolean doReadHeader() throws IOException {
    if (DEBUG) System.err.println("GPR: doReadHeader called, cur "+cur_offset+", pkt "+packet_offset);

    int tocopy = Math.min( header.length - cur_offset, pktdata.length - packet_offset );
    if (tocopy != 0) {
      System.arraycopy(pktdata, packet_offset, header, cur_offset, tocopy);
      cur_offset += tocopy;
      packet_offset += tocopy;
    }

    if (cur_offset == PACKET_HEADER_SIZE) {
      processHeader();
      cur_offset = 0;
      if (payload_length != 0) {
	payload = new byte[payload_length];
	state = STATE_READ_PAYLOAD;
	return true;
      } else {
	createPacket();
	cur_offset = 0;
	state = STATE_READ_HEADER;
	return true;
      }
    } else {
      return false;
    }
  }

  private boolean doReadPayload() throws IOException {
    if (DEBUG) System.err.println("GPR: doReadPayload called, cur "+cur_offset+", pkt "+packet_offset);

    int tocopy = Math.min( payload_length - cur_offset, pktdata.length - packet_offset );
    if (tocopy != 0) {
      System.arraycopy(pktdata, packet_offset, payload, cur_offset, tocopy);
      cur_offset += tocopy;
      packet_offset += tocopy;
    }

    if (cur_offset == payload_length) {
      createPacket();
      cur_offset = 0;
      state = STATE_READ_HEADER;
      return true;
    } else {
      return false;
    }
  }

  private void processHeader() throws IOException {
    guid = new GnutellaGUID(header, 0);
    function = header[16];
    ttl = header[17];
    hops = header[18];
    payload_length = GnutellaPacket.readLEInt(header, 19);
    if ((MAX_PAYLOAD_SIZE != -1) && (payload_length > MAX_PAYLOAD_SIZE)) {
      // Drop packet!
      throw new IOException("Invalid payload length "+payload_length);
    }
    if (payload_length < 0) {
      // Drop packet!
      throw new IOException("Invalid payload length "+payload_length);
    }

    if (DEBUG) System.err.println("GPR: read header, function "+Integer.toHexString(function & 0xff)+", ttl "+ttl+", hops "+hops+", payload_len "+payload_length);
  }


  GnutellaPacket getGnutellaPacket() throws IOException {
    synchronized (completePackets) {
      GnutellaPacket gp;
      try {
	gp = (GnutellaPacket)completePackets.firstElement();
      } catch (NoSuchElementException e) {
	return null;
      }
      if (gp != null) {
	completePackets.removeElementAt(0);
	return gp;
      } else {
	return null;
      }
    }
  }

  void createPacket() throws IOException {
    GnutellaPacket gp; 

    switch (function) {
      case GNUTELLA_FN_PING: 
  	gp = new GnutellaPingPacket(guid, ttl, hops);
	break;

      case GNUTELLA_FN_PONG:
        if (payload == null) throw new IOException("pong packet has null payload");
        gp = new GnutellaPongPacket(guid, ttl, hops, payload);
	break;

      case GNUTELLA_FN_PUSH:
        if (payload == null) throw new IOException("push packet has null payload");
        gp = new GnutellaPushPacket(guid, ttl, hops, payload);
	break;

      case GNUTELLA_FN_QUERY:
        if (payload == null) throw new IOException("query packet has null payload");
        gp =  new GnutellaQueryPacket(guid, ttl, hops, payload);
	break;

      case GNUTELLA_FN_QUERYHITS:
        if (payload == null) throw new IOException("query hits packet has null payload");
        gp = new GnutellaQueryHitsPacket(guid, ttl, hops, payload);
	break;

      default: 
        throw new IOException("GnutellaPacket got illegal function code "+Integer.toHexString(function));
    }

    completePackets.addElement(gp);
  }


}
