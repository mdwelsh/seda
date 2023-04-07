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

/**
 * This is a convenience library which makes use of "gnutellahosts.com"
 * to establish initial connections to the Gnutella network. (It can
 * also make use of any other well-known host.) It simply connects to
 * the given host and sends a single ping packet. Connections are then
 * opened to a given number of hosts returned from the resulting pongs.
 * 
 * @author Matt Welsh
 */ 

package seda.sandStorm.lib.Gnutella;

import seda.sandStorm.api.ConfigDataIF;
import seda.sandStorm.api.EventHandlerIF;
import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.QueueElementIF;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;

public class GnutellaCatcher implements EventHandlerIF {

  private static final boolean DEBUG = false;

  // Maximum number of levels of 'pong' packets before we forward
  // connection to user
  private static final int SEARCH_DEPTH = 2;

  private ManagerIF mgr;
  private GnutellaServer gs;
  private GnutellaServer mygs;
  private int total, num_established;
  private Hashtable searchTbl;

  /**
   * Create a GnutellaCatcher in the context of the given vSpace clone,
   * using the given GnutellaServer to establish new outgoing
   * connections.
   */
  public GnutellaCatcher(ManagerIF mgr, GnutellaServer gs) throws Exception {
    this.mgr = mgr;
    this.gs = gs;
    this.total = 0;
    this.num_established = 0;
    this.searchTbl = new Hashtable(1);
    mgr.createStage("GnutellaCatcher", this, null);
  }

  public void init(ConfigDataIF config) throws Exception {
    this.mygs = new GnutellaServer(mgr, config.getStage().getSink(), 0);
  }

  public void destroy() {
  }

  /** 
   * Create 'numconns' new connections to the Gnutella network, using the 
   * given hostname:port as the bootstrapping host.
   */
  public void doCatch(int numconns, String hostname, int port) throws UnknownHostException {
    this.total += numconns;
    InetAddress addr = InetAddress.getByName(hostname);
    searchDepth sd = new searchDepth();
    searchTbl.put(addr, sd);
    mygs.openConnection(addr, port);
  }

  /**
   * Create 'numconns' new connections to the Gnutella network, using
   * "gnutellahosts.com:6346" as the bootstrapping host.
   */
  public void doCatch(int numconns) throws UnknownHostException {
    doCatch(numconns, "gnutellahosts.com", GnutellaConst.DEFAULT_GNUTELLA_PORT);
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("GnutellaCatcher got: "+item);
    if (item instanceof GnutellaConnection) {
      GnutellaConnection gc = (GnutellaConnection)item;
      GnutellaPingPacket ping = new GnutellaPingPacket();
      if (DEBUG) System.err.println("GnutellaCatcher sending ping");
      gc.enqueue_lossy(ping);

    } else if (item instanceof GnutellaPongPacket) {
      GnutellaPongPacket pong = (GnutellaPongPacket)item;
      GnutellaConnection ponggc = pong.getConnection();
      InetAddress addr = ponggc.getAddress();
      searchDepth sd = (searchDepth)searchTbl.get(addr);
      if (sd == null) {
	System.err.println("GnutellaCatcher: Warning: Got pong packet from unknown address "+addr+" ("+ponggc+")");
      }

      if (sd.depth >= SEARCH_DEPTH) {
	if (num_established < total) {
	  System.err.println("GnutellaCatcher: Opening user connection to "+pong.getInetAddress().getHostAddress()+":"+pong.getPort());
	  gs.openConnection(pong.getInetAddress(), pong.getPort());
	  num_established++;
	}
      } else {
	searchDepth sd2 = new searchDepth(sd);
	searchTbl.put(pong.getInetAddress(), sd2);
	if (DEBUG) System.err.println("GnutellaCatcher: Opening depth "+sd2.depth+" connection to "+pong.getInetAddress().getHostAddress()+":"+pong.getPort());
	mygs.openConnection(pong.getInetAddress(), pong.getPort());
      }

    }
  }

  public void handleEvents(QueueElementIF qelarr[]) {
    for (int i = 0; i < qelarr.length; i++) {
      handleEvent(qelarr[i]);
    } 
  }

  private class searchDepth {
    int depth;

    searchDepth() {
      this.depth = 1;
    }

    searchDepth(searchDepth sd) {
      this.depth = sd.depth+1;
    }
  }



}

