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

import seda.sandStorm.api.*;
import seda.sandStorm.core.ssTimer;
import seda.sandStorm.lib.Gnutella.*;

import java.io.IOException;
import java.util.Hashtable;

/**
 * This is a simple Gnutella server implemented using the Sandstorm
 * Gnutella library. It correctly implements packet routing. It does
 * not respond to queries or host any shared files itself; it simply
 * routes packets to other hosts on the network. 
 *
 * @author Matt Welsh
 */
public class SimpleGnutellaServer implements EventHandlerIF {

  private static final boolean DEBUG = false;
  private static final boolean VERBOSE = true;

  // If true, periodically clean out table of previously received packets
  private static boolean DO_CLEANER = true;

  // If true, accept connections from other hosts on the Gnutella network
  private static boolean ACCEPT_CONNECTIONS = true;

  // If true, send pong messages in replies to pings - allows other
  // hosts on the network to discover us
  private static boolean SEND_PONGS = true;

  // If true, route packets
  private static boolean ROUTE_PACKETS = true;

  // Time (in ms) between iterations of the cleaner
  private static final int CLEAN_TIMER_FREQUENCY = 1000*30;

  private static String SERVER_HOSTNAME;

  // If true, establish initial connections to the Gnutella network
  // using the 'GnutellaCatcher' class
  private static boolean DO_CATCHER = true;
  private static int CATCHER_CONNECTIONS = 10;
  private static final int MIN_CONNECTIONS = 2;

  // Number of files and Kb "shared" by this server - only used for
  // filling out pong packets
  private static final int NUM_FILES = 1000;
  private static final int NUM_KB = 3000;

  private ManagerIF mgr;
  private String myName;
  private SinkIF mySink;
  private ssTimer timer;
  private GnutellaServer gs;
  private Hashtable packetTable;

  private int num_connections = 0;

  public SimpleGnutellaServer() {
  }

  public void init(ConfigDataIF config) throws Exception {
    mgr = config.getManager();
    mySink = config.getStage().getSink();
    myName = config.getStage().getName();

    // reading the config params
    String s;

    s = config.getString("server");
    if (s != null) SERVER_HOSTNAME = s;

    int port = config.getInt("port");
    if (port == -1) port = GnutellaConst.DEFAULT_GNUTELLA_PORT;

    packetTable = new Hashtable();

    try {
      gs = new GnutellaServer(mgr, mySink, port);
      if (DO_CATCHER) doCatcher();

    } catch (IOException ioe) {
      System.err.println("Could not start server: "+ioe.getMessage());
      return;
    }
    System.err.println("Created GnutellaServer: "+gs);

    if (DO_CLEANER) {
      timer = new ssTimer();
      timer.registerEvent(CLEAN_TIMER_FREQUENCY, new timerEvent(1), mySink);
    }

  }

  public void destroy() {
    System.err.println("destroy");
  }

  private void doCatcher() {
    try { 
      GnutellaCatcher catcher = new GnutellaCatcher(mgr, gs);
      if (SERVER_HOSTNAME != null) {
        catcher.doCatch(CATCHER_CONNECTIONS, SERVER_HOSTNAME, GnutellaConst.DEFAULT_GNUTELLA_PORT);
      } else {
        catcher.doCatch(CATCHER_CONNECTIONS);
      }
    } catch (Exception e) {
      System.err.println("Got exception in doCatcher: "+e);
      e.printStackTrace();
    }
  }

  // Send the packet to everyone but the originator
  private void forwardPacketToAll(GnutellaPacket pkt) {
    if ((pkt.ttl == 0) || (--pkt.ttl == 0)) {
      if (VERBOSE) System.err.println("-- Dropping packet, TTL expired: "+pkt); 
    }
    pkt.hops++;

    if (DEBUG) System.err.println("**** FORWARDING: "+pkt+" to all but "+pkt.getConnection());

    //System.err.println("FORWARDING "+pkt);
    gs.sendToAllButOne(pkt, pkt.getConnection());
  }

  // Forward an incoming packet to the corresponding source
  private void forwardPacket(GnutellaPacket pkt) {
    GnutellaConnection gc;
    gc = (GnutellaConnection)packetTable.get(pkt.getGUID());
    if (gc == null) {
      if (VERBOSE) System.err.println("-- Received reply with no request: "+pkt);
      return;
    }

    if (DEBUG) System.err.println("**** REPLYING: "+pkt+" to "+gc);

    if ((pkt.ttl == 0) || (--pkt.ttl == 0)) {
      if (VERBOSE) System.err.println("-- Dropping packet, TTL expired: "+pkt); 
    }
    pkt.hops++;
    gc.enqueue_lossy(pkt);
  }

  // Look up an older packet for responses
  // Return true if the packet is unique; false if we have seen it before
  private boolean rememberPacket(GnutellaPacket pkt) {
    GnutellaConnection gc = (GnutellaConnection)packetTable.get(pkt.getGUID());
    if (gc != null) return false;

    if (DEBUG) System.err.println("**** REMEMBERING: "+pkt+" from "+pkt.getConnection());

    packetTable.put(pkt.getGUID(), pkt.getConnection());
    return true;
  }

  /**
   * The main event handling code.
   */
  public void handleEvent(QueueElementIF item) {

    try {
      if (DEBUG) System.err.println("**** GOT: "+item);

      if (item instanceof GnutellaPingPacket) {
        GnutellaPingPacket ping = (GnutellaPingPacket)item;
        if (VERBOSE) System.err.println("-- Got ping: "+ping);

	if (ROUTE_PACKETS) {
          if (rememberPacket(ping)) {
  	    forwardPacketToAll(ping);
          }
        }
	
	if (SEND_PONGS) {
  	  GnutellaPongPacket pong = new GnutellaPongPacket(ping.getGUID(), NUM_FILES, NUM_KB);
	  if (VERBOSE) System.err.println("-- Sending pong to: "+ping.getConnection());
	  ping.getConnection().enqueue_lossy(pong);
        }

      } else if (item instanceof GnutellaQueryPacket) {
        GnutellaQueryPacket query = (GnutellaQueryPacket)item;
        if (VERBOSE) System.err.println("-- Got query: "+query.getSearchTerm());

	if (ROUTE_PACKETS) {
  	  if (rememberPacket(query)) {
  	    forwardPacketToAll(query);
          }
        }

      } else if (item instanceof GnutellaPongPacket) {
        GnutellaPongPacket pong = (GnutellaPongPacket)item;
        if (VERBOSE) System.err.println("-- Got pong: "+pong);
	if (ROUTE_PACKETS) forwardPacket(pong);

      } else if (item instanceof GnutellaQueryHitsPacket) {
        GnutellaQueryHitsPacket hits = (GnutellaQueryHitsPacket)item;
        if (VERBOSE) System.err.println("-- Got hits: "+hits);
	if (ROUTE_PACKETS) forwardPacket(hits);

      } else if (item instanceof GnutellaPushPacket) {
        if (VERBOSE) System.err.println("-- Dropping push packet (unimplemented)");

      } else if (item instanceof GnutellaConnection) {
        if (VERBOSE) System.err.println("-- New connection: "+item);
	num_connections++;

      } else if (item instanceof SinkClosedEvent) {
        if (VERBOSE) System.err.println("-- Connection closed: "+item);
	num_connections--;
	SinkClosedEvent sce = (SinkClosedEvent)item;

	if ((num_connections <= MIN_CONNECTIONS) && DO_CATCHER) doCatcher(); 

      } else if (item instanceof SinkCloggedEvent) {
        if (VERBOSE) System.err.println("-- Connection clogged: "+item);
	SinkCloggedEvent clogged = (SinkCloggedEvent)item;
	// Close down clogged connections
	GnutellaConnection gc = (GnutellaConnection)clogged.sink;
	System.err.println("GL: Closing clogged connection "+gc);
	gc.close(mySink);

      } else if (item instanceof timerEvent) {
        doTimer((timerEvent)item);
      }

    } catch (Exception e) {
      System.err.println("WORKER GOT EXCEPTION: "+e.getMessage());
      e.printStackTrace();
    }
  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

  private void doClean(timerEvent ev) {
    // Cleaner event
    if (VERBOSE) System.err.println("-- Cleaning up packetTable");

    // Might cause some recent packets to be dropped
    packetTable.clear();

    if (VERBOSE) {
      Runtime r = Runtime.getRuntime();
      System.err.println("TOTAL: "+r.totalMemory()/1024+"KB FREE: "+r.freeMemory()/1024+"KB");
    }

    // Reregister timer event
    timer.registerEvent(CLEAN_TIMER_FREQUENCY, ev, mySink);
  }

  private void doTimer(timerEvent ev) {
    if (ev.code == 1) {
      doClean(ev);
    } else {
      throw new IllegalArgumentException("Bad code in timerEvent: "+ev.code);
    }
  }

  /**
   * Small internal class to represent a timer event.
   */
  class timerEvent implements QueueElementIF {
    private int code;
    timerEvent(int code) {
      this.code = code;
    }
  }

}

