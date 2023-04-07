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

/*
 * This program accepts socket connections (from the MultiClient) application
 * and reads packets from each socket, reporting the aggregate bandwidth
 * obtained. It is capable of using blocking sockets (with one thread per
 * socket) or nonblocking socket (with a single thread).
 */

import seda.nbio.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;


// Used to keep track of socket state for event-driven server
class SockState {
  public int num_packets;
  public int current_offset;
  public NonblockingSocket nbsock;
  
  SockState(NonblockingSocket sock) {
    num_packets = current_offset = 0;
    nbsock = sock;
  }
}

public class MultiServer extends Thread {

  private static final boolean DEBUG = false;
  private static final int PORTNUM = 5721;

  private static boolean nonblocking;
  private static int MESSAGE_SIZE;
  private static final int ACK_SIZE = 32;
  private static final int SELECT_TIMEOUT = 100;
  private static final int BURST_SIZE = 1000;


  private int tnum;
  private Socket sock;

  private static Object syncobj;
  private static int num_connections = 0;
  private static int num_messages_processed = 0;
  private static long tstart;


  
  public MultiServer(Socket sock, int tnum) {
    this.sock = sock;
    this.tnum = tnum;
  }

  public void run() {
    try {
      doBandwidth();
      sock.close();
    } catch (Exception e) {
      System.err.println("Server thread "+tnum+" got exception: "+e.getMessage());
      e.printStackTrace();
    }
  }

  private static void printCumulative() {
    int total;
    long t2;
    synchronized (syncobj) {
      total = num_messages_processed;
      num_messages_processed = 0;
    }
    t2 = System.currentTimeMillis();
    printResults("CUMULATIVE BANDWIDTH", total, MESSAGE_SIZE, tstart, t2);
    System.out.println("\t"+num_connections+" connections");
    tstart = System.currentTimeMillis();
  }

  private static void printResults(String msg, int numiters, int message_size, long t1, long t2) {
    double usec = (t2-t1)*1.0e3;
    double usecper = (usec/numiters);
    double megabits = (message_size*numiters*8.0)/(1024*1024);
    double bw = (megabits * 1.0e3)/(t2-t1);

    System.out.println(msg+":");
    System.out.println("\t"+numiters+" "+message_size+"-byte messages in "+usec+" usec, or "+usecper+" usec/iter.");
    System.out.println("\t"+bw+" megabits/sec.");
  }

  private void doBandwidth() throws IOException {

    OutputStream os = sock.getOutputStream();
    InputStream is = sock.getInputStream();

    byte barr[] = new byte[MESSAGE_SIZE];
    byte barr2[] = new byte[ACK_SIZE];
    int i = 0;

    System.out.println("Connection "+tnum+" starting: message size "+MESSAGE_SIZE);

    /* Receiving */
    while (true) {
      if (DEBUG) System.err.println("["+i+"] Receiver: Receiving message...");
      int n = 0;
      while (n < barr.length) {
	n += is.read(barr, n, (barr.length - n));
      }

      i++;
      if ((i % BURST_SIZE) == 0) {
        if (DEBUG) System.err.println("Receiver: Sending ack...");
        os.write(barr2);
        os.flush();
      }

      synchronized (syncobj) {
	num_messages_processed++;
      }
      if ((tnum == 0) && (num_messages_processed >= BURST_SIZE)) printCumulative();
    }
  }

  public static void main(String args[]) {

    try {

      if ((args.length < 1) || (args.length > 2)) {
	System.err.println("Usage: java MultiServer [-n] <message size>");
	System.err.println("Options:");
	System.err.println("  -n\t\t Use nonblocking/select-based I/O");
	System.exit(-1);
      }

      int n;
      for (n = 0; n < 2; ) {
        if (args[n].equals("-n")) {
          nonblocking = true;
	  n++;
	} else {
	  break;
	}
      }

      MESSAGE_SIZE = Integer.valueOf(args[n]).intValue();

      syncobj = new Object();

      /* This is the fun part of the program: How to manage many independent
       * socket connections through the select interface.
       */
      if (nonblocking) {

	byte barr[] = new byte[MESSAGE_SIZE];
	byte barr2[] = new byte[ACK_SIZE];

	Hashtable ht = new Hashtable();
	SelectSet selset = new SelectSet();
	NonblockingServerSocket servsock = new NonblockingServerSocket(PORTNUM);
	SelectItem selitem = new SelectItem(servsock, Selectable.ACCEPT_READY);
	selset.add(selitem);

	System.err.println("Waiting for connections...");

	while (true) {
	  int r = selset.select(SELECT_TIMEOUT);
	  if (r == 0) continue;

	  SelectItem ret[] = selset.getEvents();
       	  if (ret == null) continue;

	  for (int i = 0; i < ret.length; i++) {
	    selitem = ret[i];

	    if (selitem.getSelectable() == servsock) {
	      selitem.revents = 0;
	      // New Connection
	      NonblockingSocket nbsock = servsock.nbAccept();
	      if (nbsock == null) throw new IOException("nbsock is null after nbAccept()!");
	      if (tstart == 0) tstart = System.currentTimeMillis();

	      //if (DEBUG) 
	      System.err.println("Got connection "+num_connections);
	      num_connections++;

	      SelectItem si = new SelectItem(nbsock, Selectable.READ_READY);
	      selset.add(si);
	      SockState ss = new SockState(nbsock);
	      ht.put(nbsock, ss);

	    } else {
	      //Data ready on incoming socket
      	      selitem.revents = 0;

	      NonblockingSocket nbsock = (NonblockingSocket)selitem.getSelectable();
	      SockState ss = (SockState)ht.get(nbsock);
	      if (ss == null) throw new IOException("No socket state for "+nbsock);
	      NonblockingInputStream nbis = (NonblockingInputStream)nbsock.getInputStream();
	      int c = nbis.read(barr, 0, (MESSAGE_SIZE - ss.current_offset));
	      if (c < 0) {
		// Socket must have closed
		try {
		  ss.nbsock.close();
		} catch (Exception e) {
		  // Ignore
		}
	        selset.remove(selitem);
		num_connections--;
		System.err.println("Closed connection "+num_connections);

	      } else {
		ss.current_offset += c;

		if (ss.current_offset == MESSAGE_SIZE) {
		  ss.num_packets++;
		  ss.current_offset = 0;
		  num_messages_processed++;
		  if (DEBUG) System.err.println("Finished packet "+ss.num_packets+" on connection "+nbsock);

		  // XXX Should be nonblocking - but it's short
		  if ((ss.num_packets % BURST_SIZE) == 0) {
		    OutputStream os = nbsock.getOutputStream();
		    os.write(barr2);
		    os.flush();
		  }

		  if ((num_messages_processed % BURST_SIZE) == 0) {
		    printCumulative();
		  }
		}
	      } // Got message
	    } // Data ready
	  } // For each event
	} // While (true)

      } else {
	/* Blocking case - just fork a thread for each connection */

	int conn = 0;
	long t2;
	
	ServerSocket servsock = new ServerSocket(PORTNUM);
	
	System.err.println("Waiting for connections...");
	
	while (true) {
	  Socket clisock = servsock.accept();
	  if (tstart == 0) tstart = System.currentTimeMillis();
	  MultiServer ms = new MultiServer(clisock, conn++);
	  num_connections++;
	  ms.start();
	}
      }
      
    } catch (Exception e) {
      System.out.println("MultiServer: Got exception: "+e.getMessage());
      e.printStackTrace();
    }
  }
}

