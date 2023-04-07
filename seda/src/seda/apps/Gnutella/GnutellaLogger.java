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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * This is a simple Gnutella packet router that logs the packets
 * that it receives. It does not host any files or reply to packets
 * itself; this is mainly meant to be a test application for the Sandstorm
 * Gnutella protocol library. It should be fairly easy to extend.
 *
 * Note that this code may not work with "modern" Gnutella clients;
 * it was implemented in late 2000 and has not been updated to be
 * compatible with the latest Gnutella clients and servers (e.g., LimeWire).
 *
 * @author Matt Welsh
 */
public class GnutellaLogger implements EventHandlerIF {

  private static final boolean DEBUG = false;
  private static final boolean VERBOSE = false;
  private static final boolean PRINT_HISTOGRAMS = false;

  private static boolean WRITE_LOG = true;
  private static String LOG_FILENAME = "gnutellalog.txt";
  private static boolean DO_CLEANER = true;

  private static boolean ACCEPT_CONNECTIONS = true;
  private static boolean SEND_PONGS = false;
  private static boolean ROUTE_PACKETS = true;

  private static final int LOG_TIMER_FREQUENCY = 5000;
  private static final int CLEAN_TIMER_FREQUENCY = 1000*30;

  private static String SERVER_HOSTNAME;
  private static boolean DO_CATCHER = true;
  private static int CATCHER_CONNECTIONS = 10;

  private static final int MIN_CONNECTIONS = 2;
  private static final int NUM_FILES = 1000;
  private static final int NUM_KB = 3000;

  private static final int NO_CONNECTION_DIE_THRESHOLD = 5;

  private ManagerIF mgr;
  private SinkIF mySink;
  private ssTimer timer;
  private GnutellaServer gs;
  private Hashtable packetTable;

  private FileOutputStream fos;
  private PrintWriter logps;

  private long log_time, init_time;

  private int total_timers = 0;
  private int total_packets = 0;
  private int total_bytes = 0;
  private int num_packets = 0;
  private int num_bytes = 0;
  private int num_pings = 0;
  private int num_pongs = 0;
  private int num_queries = 0;
  private int num_queryhits = 0;
  private int num_pushes = 0;
  private int total_pings = 0;
  private int total_pongs = 0;
  private int total_queries = 0;
  private int total_queryhits = 0;
  private int total_pushes = 0;
  private int num_connections = 0;
  private int num_clogged = 0;
  private int no_conn_count = 0;

  private static final int HIST_BUCKETSIZE = 5;
  private Histogram ping_hist, pong_hist, query_hist, queryhits_hist, push_hist;

  private static DecimalFormat df;

  static {
    df = new DecimalFormat();
    df.applyPattern("#.##");
  }

  public GnutellaLogger() {
    ping_hist = new Histogram(HIST_BUCKETSIZE);
    pong_hist = new Histogram(HIST_BUCKETSIZE);
    query_hist = new Histogram(HIST_BUCKETSIZE);
    queryhits_hist = new Histogram(HIST_BUCKETSIZE);
    push_hist = new Histogram(HIST_BUCKETSIZE);
  }

  public void init(ConfigDataIF config) throws Exception {
    mgr = config.getManager();
    mySink = config.getStage().getSink();

    // reading the config params
    String s;

    s = config.getString("log_filename");
    if (s != null) LOG_FILENAME = s;

    s = config.getString("server");
    if (s != null) SERVER_HOSTNAME = s;

    int port = config.getInt("port");
    if (port == -1) port = GnutellaConst.DEFAULT_GNUTELLA_PORT;

    packetTable = new Hashtable();

    try {
      openLog();

      gs = new GnutellaServer(mgr, mySink, port);

      if (DO_CATCHER) doCatcher();

    } catch (IOException ioe) {
      System.err.println("Could not start server: "+ioe.getMessage());
      return;
    }
    System.err.println("Created GnutellaServer: "+gs);

    // Start the timer
    log_time = System.currentTimeMillis();
    timer = new ssTimer();
    timer.registerEvent(LOG_TIMER_FREQUENCY, new timerEvent(0), mySink);
    if (DO_CLEANER) {
      timer.registerEvent(CLEAN_TIMER_FREQUENCY, new timerEvent(1), mySink);
    }

  }

  public void destroy() {
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
  // Return 'true' if the packet is unique; false if we have seen it
  // before
  private boolean rememberPacket(GnutellaPacket pkt) {
    GnutellaConnection gc = (GnutellaConnection)packetTable.get(pkt.getGUID());
    if (gc != null) return false;

    if (DEBUG) System.err.println("**** REMEMBERING: "+pkt+" from "+pkt.getConnection());

    packetTable.put(pkt.getGUID(), pkt.getConnection());
    return true;
  }

  public void handleEvent(QueueElementIF item) {

    try {
      if (DEBUG) System.err.println("**** SENDER GOT: "+item);

      if (item instanceof GnutellaPacket) {
   	 //System.err.println("SAW "+item);
      }

      if (item instanceof GnutellaPingPacket) {
        GnutellaPingPacket ping = (GnutellaPingPacket)item;
        logPacket(ping);

	if (ROUTE_PACKETS) {
          if (rememberPacket(ping)) {
  	    forwardPacketToAll(ping);
          }
        }
	
	if (SEND_PONGS) {
  	  GnutellaPongPacket pong = new GnutellaPongPacket(ping.getGUID(), NUM_FILES, NUM_KB);
	  if (DEBUG) System.err.println("**** SENDING PONG TO "+ping.getConnection());
	  ping.getConnection().enqueue_lossy(pong);
        }

      } else if (item instanceof GnutellaQueryPacket) {
        GnutellaQueryPacket query = (GnutellaQueryPacket)item;
        logPacket(query);
        if (VERBOSE) System.err.println("-- Got query: "+query.getSearchTerm());

	if (ROUTE_PACKETS) {
  	  if (rememberPacket(query)) {
  	    forwardPacketToAll(query);
          }
        }
  
      } else if (item instanceof GnutellaPongPacket) {
        GnutellaPongPacket pong = (GnutellaPongPacket)item;
        logPacket(pong);
	if (ROUTE_PACKETS) forwardPacket(pong);

      } else if (item instanceof GnutellaQueryHitsPacket) {
        GnutellaQueryHitsPacket hits = (GnutellaQueryHitsPacket)item;
        logPacket(hits);
	if (ROUTE_PACKETS) forwardPacket(hits);

      } else if (item instanceof GnutellaPushPacket) {
        logPacket((GnutellaPushPacket)item);
        if (VERBOSE) System.err.println("-- Dropping push packet (unimplemented)");

      } else if (item instanceof GnutellaConnection) {
        if (VERBOSE) System.err.println("-- New connection: "+item);
	if (num_connections == 0) init_time = System.currentTimeMillis();
	num_connections++;
      
      } else if (item instanceof SinkClosedEvent) {
        if (VERBOSE) System.err.println("-- Connection closed: "+item);
	num_connections--;
	SinkClosedEvent sce = (SinkClosedEvent)item;

	if ((num_connections <= MIN_CONNECTIONS) && DO_CATCHER) doCatcher(); 

      } else if (item instanceof SinkCloggedEvent) {
        if (VERBOSE) System.err.println("-- Connection closed: "+item);
	SinkCloggedEvent clogged = (SinkCloggedEvent)item;
	// Close down clogged connections
        num_clogged++;
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

  private void logPacket(GnutellaPacket packet) {
    num_packets++; total_packets++;
    int sz = packet.getSize();
    num_bytes += sz; total_bytes += sz;
    if (packet instanceof GnutellaPingPacket) {
      num_pings++; total_pings++;
      ping_hist.add(packet);
    } else if (packet instanceof GnutellaPongPacket) {
      num_pongs++; total_pongs++;
      pong_hist.add(packet);
    } else if (packet instanceof GnutellaQueryPacket) {
      num_queries++; total_queries++;
      query_hist.add(packet);
    } else if (packet instanceof GnutellaQueryHitsPacket) {
      num_queryhits++; total_queryhits++;
      queryhits_hist.add(packet);
    } else if (packet instanceof GnutellaPushPacket) {
      num_pushes++; total_pushes++;
      push_hist.add(packet);
    }
  }

  // Format decimals to 2 digits only
  private String format(double val) {
    return new String(df.format(val));
  }

  private void doLog(timerEvent ev) {
    if ((num_packets == 0) && (num_connections > 0)) {
      no_conn_count++;
      if (no_conn_count >= NO_CONNECTION_DIE_THRESHOLD) {
        // Assume something is wrong
        System.err.println("** RECEIVING NO PACKETS - ASSUMING THERE'S A PROBLEM");
        System.exit(-1);
      }
    } else {
      no_conn_count = 0;
    }

    long curtime = System.currentTimeMillis();
    long elapsed = curtime - log_time;
    long total_elapsed = curtime - init_time;
    log_time = curtime;

    total_timers++;
    double pktpersec = num_packets / (elapsed * 1.0e-3);
    double total_pktpersec = total_packets / (total_elapsed * 1.0e-3);
    double bytespersec = num_bytes / (elapsed * 1.0e-3);
    double total_bytespersec = total_bytes / (total_elapsed * 1.0e-3);

    System.err.println("---------------------------------------------");
    System.err.println(" Number of connections: "+num_connections);
    System.err.println("");
    System.err.println(" Packets: "+num_packets+", "+total_packets+" total");
    System.err.println(" Rate: "+format(total_pktpersec)+" packets/sec");
    System.err.println("       "+format(total_bytespersec)+" bytes/sec");
    if (num_packets > 0) {
      System.err.println("");
      System.err.println(" Pings:\t\t"+total_pings+" ("+(int)((total_pings * 100.0)/total_packets)+"%)");
      System.err.println(" Pongs:\t\t"+total_pongs+" ("+(int)((total_pongs * 100.0)/total_packets)+"%)");
      System.err.println(" Queries:\t"+total_queries+" ("+(int)((total_queries * 100.0)/total_packets)+"%)");
      System.err.println(" Query hits:\t"+total_queryhits+" ("+(int)((total_queryhits * 100.0)/total_packets)+"%)");
      System.err.println(" Pushes:\t"+total_pushes+" ("+(int)((total_pushes * 100.0)/total_packets)+"%)");
    }
    System.err.println("---------------------------------------------");

    if (PRINT_HISTOGRAMS) {
      ping_hist.dump(System.err, "PINGHIST"); ping_hist.clear();
      pong_hist.dump(System.err, "PONGHIST"); pong_hist.clear();
      query_hist.dump(System.err, "QUERYHIST"); query_hist.clear();
      queryhits_hist.dump(System.err, "QUERYHITSHIST"); queryhits_hist.clear();
      push_hist.dump(System.err, "PUSHHIST"); push_hist.clear();
      System.err.println("---------------------------------------------");
    }

    Date date = new Date();
    String datestr = date.toString();
    Runtime r = Runtime.getRuntime();
    long total_mem = r.totalMemory();
    long free_mem = r.freeMemory();

    logps.println(datestr+" "+num_connections+" "+total_packets+" "+format(total_pktpersec)+" "+format(total_bytespersec)+" "+num_packets+" "+format(pktpersec)+" "+format(bytespersec)+" "+num_pings+" "+num_pongs+" "+num_queries+" "+num_queryhits+" "+num_pushes+" "+total_mem+" "+free_mem+" "+num_clogged);

    num_packets = 0;
    num_bytes = 0;
    num_pings = 0;
    num_pongs = 0;
    num_queries = 0;
    num_queryhits = 0;
    num_pushes = 0;

    // Reregister
    timer.registerEvent(LOG_TIMER_FREQUENCY, ev, mySink);
  }

  private void doClean(timerEvent ev) {
    // Cleaner event
    System.err.println("-- Cleaning up packetTable");

    // Might cause some recent packets to be dropped
    packetTable.clear();
    //packetTable = new Hashtable();

    Runtime r = Runtime.getRuntime();
    System.err.println("TOTAL: "+r.totalMemory()/1024+"KB FREE: "+r.freeMemory()/1024+"KB");

    // Reregister
    timer.registerEvent(CLEAN_TIMER_FREQUENCY, ev, mySink);
  }

  private void doTimer(timerEvent ev) {
    if (ev.code == 0) {
      doLog(ev);
    } else if (ev.code == 1) {
      doClean(ev);
    } else {
      throw new IllegalArgumentException("Bad code in timerEvent: "+ev.code);
    }
  }

  private void openLog() throws IOException {
    fos = new FileOutputStream(LOG_FILENAME, true);
    logps = new PrintWriter(fos, true);
    logps.println("# date conns total_pkts total_pktpersec total_bytespersec num_pkts pktspersec bytespersec pings pongs queries queryhits pushes total_mem free_mem num_clogged");
  }

  class timerEvent implements QueueElementIF {
    private int code;
    timerEvent(int code) {
      this.code = code;
    }
  }

  class Histogram {
    private int bucketsize;
    private Hashtable buckets;

    Histogram(int bucketsize) {
      this.bucketsize = bucketsize;
      buckets = new Hashtable();
    }

    void add(GnutellaPacket packet) {
      int sz = packet.getSize();
      int bucketnum = sz / bucketsize;
      Integer bnum = new Integer(bucketnum);
      Integer val = (Integer)buckets.get(new Integer(bucketnum));
      if (val == null) {
	buckets.put(bnum, new Integer(1));
      } else {
	val = new Integer(val.intValue() + 1);
	buckets.put(bnum, val);
      }
    }

    void dump(PrintStream ps, String prefix) {
      Enumeration e = buckets.keys();
      while (e.hasMoreElements()) {
	Integer bucket = (Integer)e.nextElement();
	int origsize = bucket.intValue() * bucketsize;
	int val = ((Integer)buckets.get(bucket)).intValue();
	ps.println(prefix+" "+origsize+" "+val);
      }
    }

    void clear() {
      buckets.clear();
    }
  }


}

