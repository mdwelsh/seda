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

import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.core.ssTimer;
import seda.sandStorm.lib.aSocket.ATcpClientSocket;
import seda.sandStorm.lib.aSocket.ATcpConnection;
import seda.sandStorm.lib.aSocket.ATcpInPacket;
import seda.sandStorm.lib.util.MultiByteArrayInputStream;
import seda.util.MDWUtil;
import seda.util.StatsGatherer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.Random;
import java.util.StringTokenizer;

/**
 * This is a scalable, event-driven HTTP load generator based on the
 * Sandstorm aSocket library. A single thread is responsible for opening 
 * and managing a large number of client connections. Each client issues
 * requests according to a distribution based on the SPECWeb99 benchmark 
 * suite.
 *
 * @author Matt Welsh
 */

public class HttpLoad {
  
  private static final boolean DEBUG = false;
  private static final boolean MEMORY_DEBUG = false;

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
  private static final int MAX_REQS_PER_CONN = 5;

  // Number of bench samples before we exit; if zero, run forever
  private static int NUMBER_RUNS = 100;

  // Bucket size for connection time histogram
  private static final int CONN_HIST_BUCKETSIZE = 1;

  // Bucket size for response time histogram
  private static final int RESP_HIST_BUCKETSIZE = 1;

  // Time in msec to warm up before recording stats
  private static final long WARMUP_TIME = 20000;

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
  private static final String BOTTLENECK_URL = "/bottleneck";
  //private static final String BOTTLENECK_URL = "/cgi-bin/flash-bottleneck";
  //private static final String BOTTLENECK_URL = "/dir00000/class0_0";
  //private static final String BOTTLENECK_URL = "/index.html";
  // Frequency of bottleneck access
  private static final double BOTTLENECK_FREQ = 0.0;

  // If true, generate special 'X-Persistent' header for Flash web server
  private static final boolean FLASH_HEADERS = false;

  // If true, perform a load spike after LOAD_SPIKE_INIT_TIME 
  private static final boolean LOAD_SPIKE = true;
  private static final long LOAD_SPIKE_START_TIME = 120*1000;
  private static final long LOAD_SPIKE_FINISH_TIME = 240*1000;
  private static final int LOAD_SPIKE_SMALL_LOAD = 3;

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
  private static boolean warmup = false;

  // Array of all clientStates
  private static clientState clients[]; 
  // Map ATcpClientSocket to clientState
  private static Hashtable pendingConnTbl = new Hashtable();
  // Map ATcpClientSocket to clientState for timed out connections
  private static Hashtable timeoutConnTbl = new Hashtable();
  // Map ATcpConnection to clientState
  private static Hashtable connTbl = new Hashtable();

  private static StatsGatherer connStats, respStats, combinedRespStats,
    bottleneckQuality;
  private static int numBenchRuns = 0;
  private long totalSent = 0, totalReject = 0, totalResponse = 0;
  private static long bench_t0 = -1, bench_t1 = -1, bench_t2 = -1;

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

  public HttpLoad() {
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
      request = "GET "+baseURL.getPath()+"/dir"+(format(dir))+"/class"+theclass+"_"+file+" HTTP/1.1\r\nHost: "+baseURL.getHost()+"\r\nX-Persistent: 1\r\n\r\n";
    } else {
      request = "GET "+baseURL.getPath()+"/dir"+(format(dir))+"/class"+theclass+"_"+file+" HTTP/1.1\r\nHost: "+baseURL.getHost()+"\r\n\r\n";
    }
    return request;
  }

  /************************************************************************/

  private static void resetStats() {
    connStats = new StatsGatherer("Connect time", "CT", CONN_HIST_BUCKETSIZE);
    respStats = new StatsGatherer("Response time", "RT", RESP_HIST_BUCKETSIZE);
    combinedRespStats = new StatsGatherer("Total response time", "CRT", RESP_HIST_BUCKETSIZE);
    bottleneckQuality = new StatsGatherer("Bottleneck quality", "BQ", 1);
    total_bytes = 0;
  }

  private void doReport(double secondsPassed) {

    long num_conns = 0, num_comps = 0;
    double max_conn = 0, max_resp = 0, max_cresp = 0;
    double total_conn = 0, total_resp = 0, total_cresp = 0;

    num_conns = connStats.num;
    num_comps = respStats.num;
    max_conn = connStats.maxVal;
    max_resp = respStats.maxVal;
    max_cresp = combinedRespStats.maxVal;

    double avg_conn_time = connStats.mean();
    double avg_resp_time = respStats.mean();
    double avg_cresp_time = combinedRespStats.mean();

    double conns_per_sec = (double)num_conns*1.0 / secondsPassed;
    double comps_per_sec = (double)num_comps*1.0 / secondsPassed;
    double bytes_per_sec = (double)total_bytes*1.0 / secondsPassed;

    long numSent = 0, numReject = 0, numResponse = 0;
    for (int i = 0; i < NUM_CLIENTS; i++) {
      clientState cs = clients[i];
      numSent += cs.numRequests;
      numReject += cs.numRejects;
      numResponse += cs.numResponses;
    }
    numSent -= totalSent;
    numReject -= totalReject;
    numResponse -= totalResponse;
    totalSent += numSent;
    totalReject += numReject;
    totalResponse += numResponse;
    double freqReject = (1.0*numReject) / (1.0*(numReject+numResponse));

    System.err.println("Connect Rate:\t"+MDWUtil.format(conns_per_sec)+" connections/sec, "+num_conns+" conns");
    System.err.println("Overall rate:\t"+MDWUtil.format(comps_per_sec)+" completions/sec");
    System.err.println("Bandwidth:\t"+MDWUtil.format(bytes_per_sec)+" bytes/sec");

    System.err.println("Connect Time:\t"+MDWUtil.format(avg_conn_time)+" ms, max "+MDWUtil.format(max_conn)+" ms, 90th "+MDWUtil.format(connStats.percentile(0.9)));
    System.err.println("Response Time:\t"+MDWUtil.format(avg_resp_time)+" ms, max "+MDWUtil.format(max_resp)+" ms, 90th "+MDWUtil.format(respStats.percentile(0.9)));
    System.err.println("Combined Response Time:\t"+MDWUtil.format(avg_cresp_time)+" ms, max "+MDWUtil.format(max_cresp)+" ms, 90th "+MDWUtil.format(combinedRespStats.percentile(0.9)));

    System.err.println("Rejections:\t"+numReject+" out of "+(numReject+numResponse)+", freq "+MDWUtil.format(freqReject));
    System.err.println("Quality:\t"+bottleneckQuality.mean());

    connStats.dumpHistogram();
    respStats.dumpHistogram();
    combinedRespStats.dumpHistogram();

    resetStats();
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

	int totalSent = 0, totalReceived = 0, totalReject = 0;
	double avgSent, avgReceived, freqReject;
	for (int i = 0; i < NUM_CLIENTS; i++) {
	  clientState cs = clients[i];
	  totalSent += cs.numRequests;
	  totalReceived += cs.numResponses;
	  totalReject += cs.numRejects;
	}
	avgSent = (1.0*totalSent) / (1.0*NUM_CLIENTS);
	avgReceived = (1.0*totalReceived) / (1.0*NUM_CLIENTS);
	freqReject = (1.0*totalReject) / (1.0*totalSent);
	System.err.println("Requests sent: "+totalSent+" total, "+avgSent+" average");
	System.err.println("Responses received: "+totalReceived+" total, "+avgReceived+" average");
	System.err.println("Rejections: "+totalReject+" total, "+freqReject+" of requests rejected");
	for (int i = 0; i < NUM_CLIENTS; i++) {
	  clientState cs = clients[i];
	  System.err.println("Client "+i+" "+cs.numRequests+" sent, "+cs.numResponses+" received, "+cs.numRejects+" rejected");
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
    private int content_length = -1, header_size = 0, content_offset = 0;
    private char content_buffer[];
    private final int CONTENT_BUFFER_SIZE = 8192;
    private long tconn1, tconn2, treq1, treq2;
    private boolean newConnection = false, firstConnection = true, lastCompleted = true;
    private int numReqsThisConn = 0, numRequests = 0, numResponses = 0, numRejects = 0;
    private String curURL = null;
    private boolean closed = true;
    private boolean flagError = false;
    private boolean flagReject = false;
    private boolean firstHeaderLine = true;
    private boolean stopped = false;

    clientState(int clientnum) {
      this.clientnum = clientnum;
      this.mbs = new MultiByteArrayInputStream();
      this.lnr = new LineNumberReader(new InputStreamReader(mbs));
    }

    private void printHeader() throws IOException {
      mbs.reset();
      System.err.println("<"+clientnum+"> mbs has "+mbs.numArrays()+" arrays, "+mbs.available()+" bytes");
      LineNumberReader lnr2 = new LineNumberReader(new InputStreamReader(mbs));
      String s = lnr2.readLine();
      while (true) {
	if (s == null) break;
	System.err.println("<"+clientnum+"> Header line (len="+s.length()+"): "+s);
	if (s.length() == 0) break;
	s = lnr2.readLine();
      }
    }

    private void printContent() {
      System.err.println("\n-----------------------------------------\n");
      System.err.println("<"+clientnum+"> curURL: "+curURL+", content_length: "+content_length);
      System.err.println("\n-----------------------------------------\n");
      System.err.println("<"+clientnum+">: "+new String(content_buffer));
      System.err.println("\n=========================================\n");
    }

    void connect() {
      if (DEBUG) System.err.println("<"+clientnum+"> connect()");

      // Don't do anything if we're done
      if (stopped) {
	if (DEBUG) System.err.println("<"+clientnum+"> connect() returning, stopped");
	return;
      }

      lastCompleted = true; // Force new URL selection
      tconn1 = System.currentTimeMillis();
      clisock = new ATcpClientSocket(ADDR, PORT, eventQ);
      if (DEBUG) System.err.println("Created ClientSocket: "+clisock);
      pendingConnTbl.put(clisock, this);
      if (CONNECTION_TIMEOUT != 0) {
	timer.registerEvent(CONNECTION_TIMEOUT, new connTimeoutEvent(this), eventQ);
      }
      if (DEBUG) System.err.println("<"+clientnum+"> Trying to establish new connection");
    }

    // Called from SinkClosedEvent
    void cleanup() {
      if (DEBUG) System.err.println("<"+clientnum+"> Cleaning up");
      if (clisock != null) {
	if (DEBUG) System.err.println("Close called, removing clisock: "+clisock);
	pendingConnTbl.remove(clisock);
	timeoutConnTbl.remove(clisock);
      }
    }

    void stop() {
      if (DEBUG) System.err.println("<"+clientnum+"> Stopping");
      stopped = true;
      this.close();
    }

    void close() {
      if (DEBUG) System.err.println("<"+clientnum+"> Closing");
      closed = true;
      try {
	if (conn != null) conn.close(eventQ);
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
      if (DEBUG) System.err.println("<"+clientnum+"> newConnection");
      if (DEBUG) System.err.println("Connection completed for clisock: "+clisock);
      // First get rid of the old connection
      if (this.conn != null) {
	connTbl.remove(this.conn);
	this.conn = null;
      }

      if (clisock == null) {
	// Must have closed before finishing connection
	try {
	  conn.close(null);
	} catch (SinkException se) {
	  // Ignore
	}
	return false;
      }

      if (pendingConnTbl.remove(clisock) == null) {
	// Connection must have timed out
	if (timeoutConnTbl.remove(clisock) == null) {
	  System.err.println("cs.newConnection: WARNING: Could not find connection "+conn+" in pendingConnTbl or timeoutConnTbl!");
	}
	try {
	  System.err.println("WARNING: Connection "+toString()+" timed out");
	  conn.close(null);
	} catch (SinkClosedException sce) {
	  // Ignore (the server might have closed first)
	}
	return false;
      }

      closed = false;
      conn.startReader(eventQ);
      tconn2 = System.currentTimeMillis();
      if (REPORT && !warmup) {
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
      if (DEBUG) System.err.println("Timeout for clisock: "+clisock);
      if (pendingConnTbl.remove(clisock) == null) {
	// Connection already established
	return false;
      }
      timeoutConnTbl.put(clisock, this);
      return true;
    }

    void sendRequest() {
      if (DEBUG) System.err.println("<"+clientnum+"> sendRequest(): content_length "+content_length+" lastCompleted "+lastCompleted);

//     if ((content_length == -1) && (lastCompleted == true) && (curState != STATE_NOT_CONNECTED)) {
//	System.err.println("WARNING: sendRequest() <"+clientnum+">: Content length is -1, state is "+curState+", curURL is "+curURL+", lastCompleted is "+lastCompleted);
//     }

      if (closed == true) {
	System.err.println("WARNING: sendRequest(): Trying to send request but no connection yet");
	return;
      }

      if (conn == null) {
	System.err.println("WARNING: sendRequest(): Conn for "+toString()+" is null?");
	return;
      }


      // Only choose new URL if last one completed OK
      if (lastCompleted) {
	curURL = chooseURL();
	if (DEBUG) System.err.print("<"+clientnum+"> next url ["+curURL+"]");
	lastCompleted = false;
      } 

      if (DEBUG) System.err.println("<"+clientnum+"> sendRequest(): sending ["+curURL+"] content_length "+content_length+" lastCompleted "+lastCompleted);

      BufferElement buf = new BufferElement(curURL.getBytes());
      treq1 = System.currentTimeMillis();
      conn.enqueue_lossy(buf);
      if (!warmup) numRequests++;
      if (DEBUG) System.err.println("<"+clientnum+"> Sent request number "+numRequests);

      content_offset = 0;
      content_length = -1;
      header_size = 0;
      mbs = new MultiByteArrayInputStream();
      lnr = new LineNumberReader(new InputStreamReader(mbs));
      mbs.mark(0);
      firstHeaderLine = true;
      flagError = false;
      flagReject = false;
      header_seen = false;
    }

    // Handle the given packet, returning true if the entire response
    // has been read.
    boolean handlePacket(ATcpInPacket pkt) throws IOException {
      if (DEBUG) System.err.println("<"+clientnum+"> got packet, "+pkt.getBytes().length+" bytes");

      if (DEBUG) System.err.println("<"+clientnum+"> handlePacket(): content_length "+content_length+" lastCompleted "+lastCompleted+" header_seen "+header_seen);

      mbs.addArray(pkt.getBytes());

      if (content_length == -1) {
	String s = lnr.readLine();
	while (true) {
	  if (s == null) return false;
	  if (DEBUG) System.err.println("<"+clientnum+"> Read header line (len="+s.length()+"): "+s);
	  if (firstHeaderLine == true) {
	    firstHeaderLine = false;
	    if (DEBUG) System.err.println("<"+clientnum+"> firstheaderline "+s);
	    if (!s.startsWith("HTTP/1.1 200")) {
	      if (s.startsWith("HTTP/1.1 503")) {
  	        flagReject = true;
	      } else {
  	        flagError = true;
	      }
	    }
	  }

	  header_size += s.length();
	  if (s.startsWith("Content-Length: ")) {
	    content_length = Integer.parseInt(s.substring(16, s.length()));
	    if (DEBUG) System.err.println("<"+clientnum+"> Got content length "+content_length);
	    content_buffer = new char[content_length];
	    content_offset = 0;
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
	if (content_length == -1) {
	  System.err.println("<"+clientnum+"> WARNING: GOT HEADER BUT NO CONTENT_LENGTH:");
	  printHeader();
	  throw new Error("<"+clientnum+"> WARNING: GOT HEADER BUT NO CONTENT_LENGTH:");

	}
	while (content_offset < content_length) {
	  int toread = Math.min(CONTENT_BUFFER_SIZE, content_length - content_offset);
	  int c = lnr.read(content_buffer, content_offset, toread);
	  if (DEBUG) System.err.println("<"+clientnum+"> Read body ("+c+"/"+(content_length - content_offset)+" bytes)");
 	  if (c <= 0) return false;
	  content_offset += c;
   	}
	if (DEBUG) System.err.println("<"+clientnum+"> Finished reading body, flagError="+flagError+" flagReject="+flagReject);

	if (flagError) {
	  System.err.println("<"+clientnum+"> WARNING: Got error from server");
	  printHeader();
	  printContent();
	  flagError = false;
	  throw new Error("<"+clientnum+"> WARNING: Got error from server");
	}

	if (flagReject) {
	  if (!warmup) numRejects++;
	  flagReject = false;
	  reconnect();
	  return true;
	}

	String content_string = new String(content_buffer);
	StringTokenizer st = new StringTokenizer(content_string);
	while (st.hasMoreTokens()) {
	  if (st.nextToken().equals("quality")) {
	    String qs = st.nextToken();
	    try {
	      double quality = Double.valueOf(qs).doubleValue();
	      quality *= 100.0;
	      bottleneckQuality.add(quality);
	    } catch (NumberFormatException nfe) {
	      throw new Error("<"+clientnum+"> WARNING: Got bad quality value from server: "+qs);
	    }
	  }
	}

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
	  if (!warmup) {
	    respStats.add(resptime);
	    combinedRespStats.add(combined_resptime);
	  }
	}

	// OK, we finished this request
	lastCompleted = true;
	if (!warmup) numResponses++;
       	total_bytes += (content_length+header_size);

	if ((MAX_REQS_PER_CONN != -1) && 
	    (numReqsThisConn++ == MAX_REQS_PER_CONN)) {
	  if (DEBUG) System.err.println("<"+clientnum+"> Closing after MAX_REQS");
	  this.reconnect();

	} else {
	  if (REQUEST_DELAY > 0) { 
	    timer.registerEvent(REQUEST_DELAY, new sendRequestEvent(this), 
		eventQ);
	  } else {
	    this.sendRequest();
	  }
	}

	return true;
      } else {
	return false;
      }
    }

    // Close connection and force new connection
    private void reconnect() {
      if (DEBUG) System.err.println("<"+clientnum+"> Reconnecting");
      lastCompleted = true; // Force new URL selection
      content_offset = 0;
      content_length = -1;
      header_size = 0;
      mbs = new MultiByteArrayInputStream();
      lnr = new LineNumberReader(new InputStreamReader(mbs));
      mbs.mark(0);
      firstHeaderLine = true;
      flagError = false;
      header_seen = false;
      this.close();
    }

    public String toString() {
      return "clientState <"+clisock+">";
    }

  }

  /************************************************************************/

  // Class representing start event
  class startEvent implements QueueElementIF {
  }

  // Class representing completion of warmup phase
  class warmupDoneEvent implements QueueElementIF {
  }

  // Class representing start of load spike
  class loadSpikeStartEvent implements QueueElementIF {
  }

  // Class representing end of load spike
  class loadSpikeFinishEvent implements QueueElementIF {
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
	if (!LOAD_SPIKE) {
	  clients[n].connect();
	}
      }

      // Start small number of clients
      if (LOAD_SPIKE) {
	for (int n = 0; n < LOAD_SPIKE_SMALL_LOAD; n++) {
	  clients[n].connect();
	}
	timer.registerEvent(LOAD_SPIKE_START_TIME+WARMUP_TIME, new loadSpikeStartEvent(), eventQ);
	timer.registerEvent(LOAD_SPIKE_FINISH_TIME+WARMUP_TIME, new loadSpikeFinishEvent(), eventQ);
      }

      timer.registerEvent(WARMUP_TIME, new warmupDoneEvent(), eventQ);
      
    } else if (qel instanceof warmupDoneEvent) {
      // Finished with warmup phase
      System.err.println("*** Warmup phase finished");
      warmup = false;

      if (REPORT) {
	bench_t0 = bench_t1 = System.currentTimeMillis();
	timer.registerEvent(BENCH_DELAY, new benchmarkEvent(), eventQ);
      }

    } else if (qel instanceof loadSpikeStartEvent) {
      // Start load spike
      System.err.println("*** Starting load spike");

      for (int n = LOAD_SPIKE_SMALL_LOAD; n < NUM_CLIENTS; n++) {
	clients[n].connect();
      }

    } else if (qel instanceof loadSpikeFinishEvent) {
      // Finish load spike
      System.err.println("*** Finishing load spike");

      for (int n = LOAD_SPIKE_SMALL_LOAD; n < NUM_CLIENTS; n++) {
	clients[n].stop();
      }

    } else if (qel instanceof ATcpConnection) {
      ATcpConnection conn = (ATcpConnection)qel;
      clientState cs = (clientState)pendingConnTbl.get(conn.getClientSocket());
      if (cs == null) {
	// Might have timed out
	cs = (clientState)timeoutConnTbl.get(conn.getClientSocket());
	if (cs == null) {
	  System.err.println("handleEvent: WARNING: No pending or timeout connection found for "+conn+" ("+conn.getClientSocket()+")");
	  try {
	    conn.close(null);
	  } catch (Exception e) {
	    System.err.println("handleEvent: WARNING: Got exception closing unknown connection: "+e);
	  }
	  return;
	}
      }
      if (cs.newConnection(conn)) {
	if (cs.numRequests == 0) {
	  timer.registerEvent(CONNECT_DELAY, new sendRequestEvent(cs), 
      	      eventQ);
	} else {
	  if (DEBUG) System.err.println("Sending request on <"+cs.clientnum+"> from new connection");
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
      SinkClosedEvent sce = (SinkClosedEvent)qel;
      clientState cs = (clientState)connTbl.get(sce.sink);
      if (cs == null) { 
	System.err.println("handleEvent: WARNING: Got SinkClosedEvent for unknown connection: "+sce.sink);
	return;
      }
      cs.cleanup(); // Clear old connection state 
      cs.connect();

    } else if (qel instanceof connTimeoutEvent) {
      connTimeoutEvent cte = (connTimeoutEvent)qel;
      clientState cs = cte.cs;
      if (cs.timeoutConnection(cte)) {
	cs.connect();
      }

    } else if (qel instanceof sendRequestEvent) {
      sendRequestEvent sre = (sendRequestEvent)qel;
      if (DEBUG) System.err.println("Sending request on <"+sre.cs.clientnum+"> from sendRequestEvent");
      sre.cs.sendRequest();

    } else if (qel instanceof benchmarkEvent) {
      doBenchmark();

    } else {
      System.err.println("handleEvent: WARNING: Unexpected event: "+qel);
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
      clients[i].stop();
    }
  }

  private void go() {
    timer.registerEvent(1000, new startEvent(), eventQ);
    eventLoop();
  }

  /************************************************************************/

  private static void usage() {
    System.err.println("usage: HttpLoad <baseurl> <numclients> <request delay (ms)> <total connection load> <number of runs>");
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

      System.err.println("HttpLoad: Base URL "+baseURL+", "+NUM_CLIENTS+" clients, "+REQUEST_DELAY+" ms delay, "+LOAD_CONNECTIONS+" total load connections, "+NUMBER_RUNS+" runs");

      setupDists();
      System.err.println("Number of directories: "+NUMDIRS);

      timer = new ssTimer();
      eventQ = new FiniteQueue();

      if (REPORT) {
	resetStats();
      }

      new HttpLoad().go();

    } catch (Exception e) {
      System.err.println("main() got exception: "+e);
      e.printStackTrace();
    }
    System.err.println("main() returning");
  }

}

