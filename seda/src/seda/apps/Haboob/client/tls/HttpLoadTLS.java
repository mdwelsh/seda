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

import seda.lib.aTLS.*;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.QueueIF;
import seda.sandStorm.api.SinkClosedEvent;
import seda.sandStorm.api.SinkClosedException;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.core.ssTimer;
import seda.sandStorm.lib.aSocket.ATcpClientSocket;
import seda.sandStorm.lib.aSocket.ATcpConnection;
import seda.sandStorm.lib.aSocket.ATcpInPacket;
import seda.sandStorm.lib.util.MultiByteArrayInputStream;
import seda.util.MDWUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;

/**
 * This is an HTTP load generator which operates similarly to that used
 * in the SPECweb99 benchmark. Unlike SPECweb99, it only accesses static
 * pages and measures throughput and request/connect time latency (rather
 * than the number of simultaneous "valid" connections).
 *
 * This version of HttpLoad makes use of the aSocket library and a single
 * thread to simulate many clients. It does not seem to work well when
 * benchmarking Apache and Flash, so I recommend using HttpLoadThreaded
 * instead.
 *
 * XXX XXX XXX MDW: This version taken from denchi 1-Nov-01. It seems
 * to be based on an older HttpLoad so it may have some bugs. Should
 * merge this code with the latest HttpLoad.java.
 *
 * @author Matt Welsh
 */

public class HttpLoadTLS {
  
  private static final boolean DEBUG = false;
  private static final boolean MEMORY_DEBUG = false;
    private static final boolean CHIDEBUG = false;

  private static final boolean REPORT = true;
  private static final boolean OVERALL_REPORT = false;
  private static final boolean CONTINUOUS_REPORT = true;

  // Time in ms between reporting measurements
  private static final int BENCH_DELAY = 5000;

  // Number of samples to skip at beginning of run
  public static final int SKIP_SAMPLES = 0;

  // Wait this long before actually doing data send (first connection only)
  private static final int CONNECT_DELAY = 1000;

  // Timeout for initiating a new connection; 0 if no timeout
  private static final long CONNECTION_TIMEOUT = 0;

  // Number of requests before closing connection; -1 for infinite
  public static final int MAX_REQS_PER_CONN = 5;

  // Number of bench samples before we exit; if zero, run forever
  private static int NUMBER_RUNS = 100;

  // Bucket size for connection time histogram
  private static final int CONN_HIST_BUCKETSIZE = 1;

  // Bucket size for response time histogram
  private static final int RESP_HIST_BUCKETSIZE = 1;

  // Number of classes
  private static final int NUMCLASSES = 4;
  // Number of directories - based on load value
  private static int NUMDIRS;
  // Number of files
  private static final int NUMFILES = 8;
  // Zipf distribution table for directory
  private static double DIR_ZIPF[];
  // Zipf distribution table for file
  private static double FILE_ZIPF[];
  // Frequency of each class
  private static final double CLASS_FREQ[] = { 0.35, 0.50, 0.14, 0.01 };
  // Order of file popularity within each class
  private static final int FILE_ORDER[] = { 4, 3, 5, 2, 6, 1, 7, 8, 0 };

  // URL to trigger server bottleneck
    //private static final String BOTTLENECK_URL = "/bottleneck";
    // DC added this for testing purpose
    private static final String BOTTLENECK_URL = "/dir00001/class1_7";

    //private static final String BOTTLENECK_URL = "/cgi-bin/flash-bottleneck";
  // Frequency of bottleneck access
  private static final double BOTTLENECK_FREQ = 1.0;

  // If true, generate special 'X-Persistent' header for Flash web server
  private static final boolean FLASH_HEADERS = true;

  private static Random rand;
  private static URL baseURL;
  private static InetAddress ADDR;
  private static int PORT;
  private static int NUM_CLIENTS;
  private static int REQUEST_DELAY;
  private static int LOAD_CONNECTIONS; 

  private static QueueIF eventQ;
  private static ssTimer timer;
  private static int total_bytes = 0;
  private static boolean timeToQuit = false;

  // Array of all clientStates
  private static clientState clients[]; 
  // Map ATcpClientSocket to clientState
  private static Hashtable pendingConnTbl = new Hashtable();
  // Map ATcpClientSocket to clientState for timed out connections
  private static Hashtable timeoutConnTbl = new Hashtable();
  // Map ATcpConnection to clientState
  private static Hashtable connTbl = new Hashtable();

  private static statsGatherer connStats, respStats, combinedRespStats;
  private static int numBenchRuns = 0;
  private static long bench_t0 = -1, bench_t1 = -1, bench_t2 = -1;

  // used to create a secure connection
    private final static boolean useSecure = true;

  private final static String rootfile = "keys/root.pem";
  private final static String keyfile = "keys/client.pem";
  private final static String randomfile = "/tmp/random.pem"; 
    //private final static String randomfile = "../../../../denchi/puretlsnew/random.pem";
  private final static String password = "password";

  // Used to format numbers for URL generation
  private static DecimalFormat df;
  static {
    df = new DecimalFormat();
    df.applyPattern("00000");
  }
  private static String format(int val) {
    return df.format((long)val);
  }

  /************************************************************************/

  public HttpLoadTLS() {
  }

  /************************************************************************/

  // Setup table of Zipf distribution values according to given size
  private static double[] setupZipf(int size) {
    double table[] = new double[size+1];
    double zipf_sum;
    int i;

    for (i = 1; i <= size; i++) {
      table[i] = (double)1.0 / (double)i;
    }

    zipf_sum = 0.0;
    for (i = 1; i <= size; i++) {
      zipf_sum += table[i];
      table[i] = zipf_sum;
    }
    table[size] = 0.0;
    table[0] = 0.0;
    for (i = 0; i < size; i++) {
      table[i] = 1.0 - (table[i] / zipf_sum);
    }
    return table;
  }

  // Set up distribution tables
  private static void setupDists() {
    rand = new Random();

    // Compute number of directories according to SPECweb99 rules
    double opsps = (400000.0 / 122000.0) * LOAD_CONNECTIONS;
    NUMDIRS = (int)(25 + (opsps/5));
    DIR_ZIPF = setupZipf(NUMDIRS);
    FILE_ZIPF = setupZipf(NUMFILES);

    // Sum up CLASS_FREQ table 
    for (int i = 1; i < CLASS_FREQ.length; i++) {
      CLASS_FREQ[i] += CLASS_FREQ[i-1];
    }
  }

  // Return index into Zipf table of random number chosen from 0.0 to 1.0
  private static int zipf(double table[]) {
    double r = rand.nextDouble();
    int i = 0;
    while (r < table[i]) {
      i++;
    }
    return i-1;
  }

  private String chooseURL() {
    double d = rand.nextDouble();
    if ((BOTTLENECK_FREQ > 0) && (d <= BOTTLENECK_FREQ)) {
      if (FLASH_HEADERS) {
	return "GET "+BOTTLENECK_URL+" HTTP/1.1\r\nHost: "+baseURL.getHost()+"\r\nX-Persistent: 1\r\n\r\n";
      } else {
  	return "GET "+BOTTLENECK_URL+" HTTP/1.1\r\nHost: "+baseURL.getHost()+"\r\n\r\n";
      }
    }

    int dir = zipf(DIR_ZIPF);
    int file = FILE_ORDER[ zipf(FILE_ZIPF) ];

    int theclass = 0;
    d = rand.nextDouble();
    while (d > CLASS_FREQ[theclass]) theclass++;

    String request;
    if (FLASH_HEADERS) {
      request = "GET /dir"+(format(dir))+"/class"+theclass+"_"+file+" HTTP/1.1\r\nHost: "+baseURL.getHost()+"\r\nX-Persistent: 1\r\n\r\n";
    } else {
      request = "GET /dir"+(format(dir))+"/class"+theclass+"_"+file+" HTTP/1.1\r\nHost: "+baseURL.getHost()+"\r\n\r\n";
    }
    return request;
  }

  /************************************************************************/

  private static void resetStats() {
    connStats = new statsGatherer("Connect time", "CT", CONN_HIST_BUCKETSIZE);
    respStats = new statsGatherer("Response time", "RT", RESP_HIST_BUCKETSIZE);
    combinedRespStats = new statsGatherer("Total response time", "CRT", RESP_HIST_BUCKETSIZE);
  }

  private void doReport(double secondsPassed) {
    long my_total_bytes;
    statsGatherer myConnStats, myRespStats, myCombinedRespStats;

    myConnStats = connStats;
    myRespStats = respStats;
    myCombinedRespStats = combinedRespStats;
    my_total_bytes = total_bytes; total_bytes = 0;

    resetStats();

    long num_conns = myConnStats.num;
    long num_comps = myRespStats.num;
    long max_conn = myConnStats.maxVal;
    long max_resp = myRespStats.maxVal;
    long max_cresp = myCombinedRespStats.maxVal;
    double avg_conn_time = myConnStats.mean();
    double avg_resp_time = myRespStats.mean();
    double avg_cresp_time = myCombinedRespStats.mean();

    double conns_per_sec = (double)num_conns*1.0 / secondsPassed;
    double comps_per_sec = (double)num_comps*1.0 / secondsPassed;
    double bytes_per_sec = (double)my_total_bytes*1.0 / secondsPassed;

    System.err.println("Connect Rate:\t"+MDWUtil.format(conns_per_sec)+" connections/sec, "+num_conns+" conns");
    System.err.println("Overall rate:\t"+MDWUtil.format(comps_per_sec)+" completions/sec");
    System.err.println("Bandwidth:\t"+MDWUtil.format(bytes_per_sec)+" bytes/sec");

    System.err.println("Connect Time:\t"+MDWUtil.format(avg_conn_time)+" ms, max "+MDWUtil.format(max_conn)+" ms");
    System.err.println("Response Time:\t"+MDWUtil.format(avg_resp_time)+" ms, max "+MDWUtil.format(max_resp)+" ms");
    System.err.println("Combined Response Time:\t"+MDWUtil.format(avg_cresp_time)+" ms, max "+MDWUtil.format(max_cresp)+" ms");

    myConnStats.dumpHistogram();
    myRespStats.dumpHistogram();
    myCombinedRespStats.dumpHistogram();
  }

  private void doBenchmark() {


    bench_t2 = System.currentTimeMillis();

    if (CONTINUOUS_REPORT) {
      doReport(((bench_t2 - bench_t1)*1.0e-3));
    }

    numBenchRuns++;

    if (numBenchRuns == NUMBER_RUNS) {

      System.err.println("Benchmark waiting for clients to quit...\n");
      stopAllClients();

      if (OVERALL_REPORT) {
	bench_t2 = System.currentTimeMillis();
	doReport(((bench_t2 - bench_t0)*1.0e-3));
      }

      if (REPORT) {
	System.err.println("Fairness report:");

	int totalSent = 0, totalReceived = 0;
	double avgSent, avgReceived;
	for (int i = 0; i < NUM_CLIENTS; i++) {
	  clientState cs = clients[i];
	  totalSent += cs.numRequests;
	  totalReceived += cs.numResponses;
	}
	avgSent = totalSent / NUM_CLIENTS;
	avgReceived = totalReceived / NUM_CLIENTS;
	System.err.println("Requests sent: "+totalSent+" total, "+avgSent+" average");
	System.err.println("Responses received: "+totalReceived+" total, "+avgReceived+" average");
	for (int i = 0; i < NUM_CLIENTS; i++) {
	  clientState cs = clients[i];
	  System.err.println("Client "+i+" "+cs.numRequests+" sent, "+cs.numResponses+" received");
	}

	// All done!
	System.exit(0);
      }
    }

    if (MEMORY_DEBUG) {
      Runtime r = Runtime.getRuntime();
      System.err.println("Total memory in use: "+r.totalMemory()/1024+" KB, free "+r.freeMemory()/1024+" KB");
      System.err.println("connTbl size "+connTbl.size());
      System.err.println("pendingConnTbl size "+pendingConnTbl.size());
      System.err.println("timeoutConnTbl size "+timeoutConnTbl.size());
    }

    if (REPORT) timer.registerEvent(BENCH_DELAY, new benchmarkEvent(), eventQ);
    bench_t1 = System.currentTimeMillis();

  }


  /************************************************************************/

  class clientState {
    private int clientnum;
    private ATcpClientSocket clisock = null;
    private ATcpConnection conn = null;
    private MultiByteArrayInputStream mbs = null;
    private LineNumberReader lnr = null;
    private boolean header_seen = false;
    private int content_length = -1, header_size = 0;
    private int count = 0;
    private char readBuffer[];
    private final int readBufferSize = 8192;
    private long tconn1, tconn2, treq1, treq2;
    private boolean newConnection = false, firstConnection = true, lastCompleted = true;
    private int numReqsThisConn = 0, numRequests = 0, numResponses = 0;
    private String url = null;

    clientState(int clientnum) {
      this.clientnum = clientnum;
      this.mbs = new MultiByteArrayInputStream();
      this.lnr = new LineNumberReader(new InputStreamReader(mbs));
      this.readBuffer = new char[readBufferSize];
    }

    void connect() {
      tconn1 = System.currentTimeMillis();
      if (useSecure) {
	  try {
	      if (CHIDEBUG) System.err.println ("HttpLoad: Creating an aTLSclientSocket!");
	      // XXX MDW - should this be done every time ?
	      aTLSClientSocket.initialize(rootfile, keyfile, randomfile, password);
	      if (CHIDEBUG) {
		  System.err.println ("this is the port: " + PORT);
	      }
	      clisock = new aTLSClientSocket(ADDR, PORT, eventQ);
	      // profile
	      System.err.println ("DENCHI: HttpLoad.connect created new aTLSclientsocket, so connection process start again");
	  }
	  catch (Exception e) {
	      System.err.println ("HttpLoad: Exception trying to create aTLSClientSocket: " + e);
	      return;
	  }
      }
      else {
	  if (CHIDEBUG) System.err.println ("HttpLoad: Not using a secure connection!");
	  clisock = new ATcpClientSocket(ADDR, PORT, eventQ);
      }
      if (CHIDEBUG) {
	  System.err.println ("HTTPLOAD: putting the clisock that was just created into the hashtable!!!!!!");
	  System.err.println ("lets make sure that it's not null");
	  if (clisock == null) {
	      System.err.println ("WE HAVE A BIG PROBLEM!!!!!");
	  }
	  else {
	      System.err.println ("LOOKS OKAY");
	  }
      }

      pendingConnTbl.put(clisock, this);
      if (CONNECTION_TIMEOUT != 0) {
	timer.registerEvent(CONNECTION_TIMEOUT, new connTimeoutEvent(this), eventQ);
      }
      if (DEBUG) System.err.println("<"+clientnum+"> Trying to establish new connection");
    }

    void close() {
      if (clisock != null) timeoutConnTbl.remove(clisock);
      try {
	  // profile purposes
	  //if (conn != null) conn.close(eventQ);
	  if (conn != null) {
	      System.err.println ("DENCHI: HttpLoad going to call close on the connection at " + System.currentTimeMillis());
	      conn.close(eventQ);
	  }
      } catch (SinkClosedException sce) {
	// Ignore 
      }

      // Don't remove conn and connTbl entry - want the SinkClosedEvent
      // to look us up in connTbl and trigger a new connection 
      this.clisock = null;
      numReqsThisConn = 0;
      if (DEBUG) System.err.println("<"+clientnum+"> Closed connection");
    }

    // Handle an incoming connection, returns true if established
    // (i.e., not timed out)
    boolean newConnection(ATcpConnection conn) {
      if (pendingConnTbl.remove(clisock) == null) {
	// Connection must have timed out
	if (timeoutConnTbl.remove(clisock) == null) {
	  System.err.println("cs.newConnection: WARNING: Could not find connection "+conn+" in pendingConnTbl or timeoutConnTbl!");
	}
	try {
	  System.err.println("Connection "+toString()+" timed out");
	  conn.close(null);
	} catch (SinkClosedException sce) {
	  // Ignore (the server might have closed first)
	}
	if (DEBUG) System.err.println("<"+clientnum+"> Got new connection, but timed out");
	return false;
      }

      // First get rid of the old connection
      if (this.conn != null) {
	connTbl.remove(this.conn);
	this.conn = null;
      }
      conn.startReader(eventQ);
      tconn2 = System.currentTimeMillis();
      if (REPORT) {
	long conntime = tconn2 - tconn1;
	connStats.add(conntime);
      }

      if (DEBUG) System.err.println("<"+clientnum+"> Got new connection");
      connTbl.put(conn, this);
      this.conn = conn;
      newConnection = true;
      return true;
    }

    // Handle a connection timeout event
    boolean timeoutConnection(connTimeoutEvent cte) {
      if (pendingConnTbl.remove(clisock) == null) {
	// Connection already established
	return false;
      }
      timeoutConnTbl.put(clisock, this);
      return true;
    }

    void sendRequest() {

      if (conn == null) {
	System.err.println("WARNING: sendRequest(): Conn for "+toString()+" is null?");
	return;
      }
      // Only choose new URL if last one completed OK
      if (lastCompleted) {
	url = chooseURL();
	lastCompleted = false;
      } 
      if (DEBUG) System.err.println("<"+clientnum+"> Sending request: "+url);
      BufferElement buf = new BufferElement(url.getBytes());
      treq1 = System.currentTimeMillis();
      conn.enqueue_lossy(buf);
      numRequests++;
      if (DEBUG) System.err.println("<"+clientnum+"> Sent request number "+numRequests);

      count = 0;
      content_length = -1;
      header_size = 0;
      mbs = new MultiByteArrayInputStream();
      lnr = new LineNumberReader(new InputStreamReader(mbs));
    }

    // Handle the given packet, returning true if the entire response
    // has been read.
    boolean handlePacket(ATcpInPacket pkt) throws IOException {
      if (DEBUG) System.err.println("<"+clientnum+"> got packet, "+pkt.getBytes().length+" bytes");
      mbs.addArray(pkt.getBytes());

      if (content_length == -1) {
	String s = lnr.readLine();
	while (true) {
	  if (s == null) return false;
	  if (DEBUG) System.err.println("<"+clientnum+"> Read header line (len="+s.length()+"): "+s);
	  header_size += s.length();
	  if (s.startsWith("Content-Length: ")) {
	    content_length = Integer.parseInt(s.substring(16, s.length()));
	    if (DEBUG) System.err.println("<"+clientnum+"> Got content length "+content_length);
	  }
	  if (s.length() == 0) {
	    header_seen = true;
	    break;
	  } else {
	    s = lnr.readLine();
	  }
	} 
      }

      if (DEBUG) System.err.println("<"+clientnum+"> Header size "+header_size);

      if (header_seen) {
	while (count < content_length) {
	  int toread = Math.min(readBufferSize, (content_length - count));
	  int c = lnr.read(readBuffer, 0, toread);
	  if (DEBUG) System.err.println("<"+clientnum+"> Read body ("+c+"/"+(content_length - count)+" bytes)");
 	  if (c <= 0) return false;
	  count += c;
   	}
	if (DEBUG) System.err.println("<"+clientnum+"> Finished reading body");

	treq2 = System.currentTimeMillis();
	if (REPORT) {
	  long resptime = treq2 - treq1;
	  long combined_resptime;
	  if (newConnection) {
	    if (firstConnection) {
	      combined_resptime = resptime;
	      firstConnection = false;
	    } else {
	      combined_resptime = treq2 - tconn1;
	    }
	    newConnection = false;
	  } else {
	    combined_resptime = resptime;
	  }
	  respStats.add(resptime);
	  combinedRespStats.add(combined_resptime);
	}

	if ((MAX_REQS_PER_CONN != -1) && 
	    (numReqsThisConn++ == MAX_REQS_PER_CONN)) {
	  this.close();
	  // MDW MDW XXX XXX XXX TESTING !!!!!!!
	  //this.connect();

	} else {
	  if (REQUEST_DELAY > 0) { 
	    timer.registerEvent(REQUEST_DELAY, new sendRequestEvent(this), 
		eventQ);
	  }
	}

	// OK, we finished this request
	lastCompleted = true;
	numResponses++;
       	total_bytes += (content_length+header_size);
	return true;
      } else {
	return false;
      }
    }

    public String toString() {
      return "clientState <"+clisock+">";
    }

  }

  /************************************************************************/

  // Class representing start event
  class startEvent implements QueueElementIF {
  }

  // Class representing connection timeout
  class connTimeoutEvent implements QueueElementIF {
    clientState cs;
    connTimeoutEvent(clientState cs) {
      this.cs = cs;
    }
    public String toString() {
      return "connTimeoutEvent <"+cs+">";
    }
  }

  // Class representing timer for next request
  class sendRequestEvent implements QueueElementIF {
    clientState cs;
    sendRequestEvent(clientState cs) {
      this.cs = cs;
    }
  }

  // Class representing benchmark event
  class benchmarkEvent implements QueueElementIF {
  }

  /************************************************************************/

  private void handleEvent(QueueElementIF qel) {
    if (DEBUG) System.err.println("handleEvent: GOT "+qel);

    if (timeToQuit) return; // Drop if done

    if (qel instanceof startEvent) {
      // Create clients and start

      clients = new clientState[NUM_CLIENTS];
      for (int n = 0; n < NUM_CLIENTS; n++) {
	clients[n] = new clientState(n);
	clients[n].connect();
      }

      if (REPORT) {
	bench_t0 = bench_t1 = System.currentTimeMillis();
	timer.registerEvent(BENCH_DELAY, new benchmarkEvent(), eventQ);
      }

    } else if (qel instanceof ATcpConnection) {
	if (CHIDEBUG) System.err.println ("httpload: handling a new atcpconnection in handleEvent()");
	if (qel instanceof aTLSConnection) {
	    if (CHIDEBUG) {
		System.err.println ("the connection is atls connection, so taht's working right");
	    }
	}
      ATcpConnection conn = (ATcpConnection)qel;
      if (conn.getClientSocket() == null) {
	  if (DEBUG) System.err.println ("this si the culprit baby");
      }
      else {
	  if (DEBUG) System.err.println ("tis is okay, no culprit");
      }
      clientState cs = (clientState)pendingConnTbl.get(conn.getClientSocket());
      if (cs == null) {
	// Might have timed out
	  if (CHIDEBUG) System.err.println ("httpload: it wasn't inside the pending conn table anymore");
	cs = (clientState)timeoutConnTbl.get(conn.getClientSocket());
	if (cs == null) {
	  System.err.println("handleEvent: WARNING: No pending or timeout connection found for "+conn);
	  return;
	}
      }
      if (cs.newConnection(conn)) {
	if (cs.numRequests == 0) {
	  timer.registerEvent(CONNECT_DELAY, new sendRequestEvent(cs), 
      	      eventQ);
	} else {
	  cs.sendRequest();
	}
      }

    } else if (qel instanceof ATcpInPacket) {
      ATcpInPacket pkt = (ATcpInPacket)qel;
      clientState cs = (clientState)connTbl.get(pkt.getConnection());
      if (cs == null) {
	System.err.println("handleEvent: WARNING: No clientState found for "+pkt.getConnection());
	return;
      }

      try {
	cs.handlePacket(pkt);
      } catch (IOException ioe) {
	System.err.println("WARNING: Got IOException from handlePacket: "+ioe);
	ioe.printStackTrace();
      }

    } else if (qel instanceof SinkClosedEvent) {
	// profile things
	System.err.println("DENCHI: HttpLoad: Got SinkClosedEvent "+qel);
      SinkClosedEvent sce = (SinkClosedEvent)qel;
      clientState cs = (clientState)connTbl.get(sce.sink);
      if (cs == null) { 
	System.err.println("WARNING: Got SinkClosedEvent for unknown connection: "+sce.sink);
	return;
      }

      // XXX MDW: don't need this
//    cs.close(); // Clear old connection state 
      cs.connect();

    } else if (qel instanceof connTimeoutEvent) {
      connTimeoutEvent cte = (connTimeoutEvent)qel;
      clientState cs = cte.cs;
      if (cs.timeoutConnection(cte)) {
	cs.connect();
      }

    } else if (qel instanceof sendRequestEvent) {
      sendRequestEvent sre = (sendRequestEvent)qel;
      sre.cs.sendRequest();

    } else if (qel instanceof benchmarkEvent) {
      doBenchmark();

    } else {
      System.err.println("handleEvent: Unexpected event: "+qel);
    }

  }

  private void eventLoop() {
    QueueElementIF fetched[];

    while (true) {
      while ((fetched = eventQ.blocking_dequeue_all(0)) == null) ;
      for (int i = 0; i < fetched.length; i++) {
	handleEvent(fetched[i]);
      }
    }
  }

  private void stopAllClients() {
    for (int i = 0; i < NUM_CLIENTS; i++) {
      timeToQuit = true;
      clients[i].close();
    }
  }

  private void go() {
    timer.registerEvent(1000, new startEvent(), eventQ);
    eventLoop();
  }

  /************************************************************************/

  private static void usage() {
    System.err.println("usage: HttpLoadTLS <baseurl> <numclients> <request delay (ms)> <total connection load> <number of runs>");
    System.exit(1);
  }

  public static void main(String args[]) {

    if (args.length != 5) usage();

    try {
      baseURL = new URL(args[0]);
      ADDR = InetAddress.getByName(baseURL.getHost());
      PORT = baseURL.getPort();
      if (PORT == -1) PORT = 80;

      NUM_CLIENTS = Integer.decode(args[1]).intValue();
      REQUEST_DELAY = Integer.decode(args[2]).intValue();
      LOAD_CONNECTIONS = Integer.decode(args[3]).intValue();
      NUMBER_RUNS = Integer.decode(args[4]).intValue();

      System.err.println("HttpLoadTLS: Base URL "+baseURL+", "+NUM_CLIENTS+" clients, "+REQUEST_DELAY+" ms delay, "+LOAD_CONNECTIONS+" total load connections, "+NUMBER_RUNS+" runs");

      setupDists();
      System.err.println("Number of directories: "+NUMDIRS);

      timer = new ssTimer();
      eventQ = new FiniteQueue();

      if (REPORT) {
	resetStats();
      }

      new HttpLoadTLS().go();

    } catch (Exception e) {
      System.err.println("main() got exception: "+e);
      e.printStackTrace();
    }
    System.err.println("main() returning");
  }

}

/************************************************************************/

class statsGatherer {

  private Hashtable histogram;
  private int bucketSize;
  private String name;
  private String tag;
  private double mean = -1;

  private int skip = 0;
  int num = 0;
  long maxVal = 0;
  long cumulativeVal = 0;

  statsGatherer(String name, String tag, int bucketSize) {
    this.name = name;
    this.tag = tag;
    this.bucketSize = bucketSize;
    if (bucketSize != 0) {
      histogram = new Hashtable(1);
    }
  }

  synchronized void add(long val) {
    if (skip < HttpLoadTLS.SKIP_SAMPLES) {
      skip++;
      return;
    }

    num++;

    if (val > maxVal) maxVal = val;
    cumulativeVal += val;

    if (bucketSize != 0) {
      Integer ct = new Integer((int)val / bucketSize);
      Integer bval = (Integer)histogram.remove(ct);

      if (bval == null) {
	histogram.put(ct, new Integer(1));
      } else {
	bval = new Integer(bval.intValue() + 1);
	histogram.put(ct, bval);
      }
    }
  }

  synchronized void dumpHistogram() {
    Enumeration e = histogram.keys();
    while (e.hasMoreElements()) {
      Integer bucket = (Integer)e.nextElement();
      int time = bucket.intValue() * bucketSize;
      int val = ((Integer)histogram.get(bucket)).intValue();
      System.err.println(tag+" "+time+" ms "+val+" count");
    }
    System.err.println("\n");
  }

  double mean() {
    if (num == 0) return 0.0;
    return (cumulativeVal * 1.0)/num;
  }

}


