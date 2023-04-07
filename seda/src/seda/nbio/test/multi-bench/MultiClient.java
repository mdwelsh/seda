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
 * This program creates a number of outgoing socket connections
 * (each handled by its own thread), and writes a stream of packets to
 * each socket. Using the -q option makes the client only open a 
 * connection, and not send any data; this is used to measure the overhead
 * of idle connections to a server.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

public class MultiClient extends Thread {

  private static final boolean DEBUG = false;
  private static final int PORTNUM = 5721;

  private static String REMOTE_HOST;
  private static int NUM_CONNECTIONS;
  private static int MESSAGE_SIZE;
  private static final int ACK_SIZE = 32;
  private static final int BURST_SIZE = 1000; 
  private static boolean QUIET;
  
  private Socket sock;
  private int tnum;

  private static void printResults(String msg, int numiters, int message_size, long t1, long t2) {
    double usec = (t2-t1)*1.0e3;
    double usecper = (usec/numiters);
    double megabits = (message_size*numiters*8.0)/(1024*1024);
    double bw = (megabits * 1.0e3)/(t2-t1);

    System.out.println(msg+":");
    System.out.println("\t"+numiters+" "+message_size+"-byte messages in "+usec+" usec, or "+usecper+" usec/iter.");
    System.out.println("\t"+bw+" megabits/sec.");
  }

  public MultiClient(int tnum) {
    this.tnum = tnum;
  }

  public void run() {
    try {

      // Sleep for random time before making connection
      Random r = new Random();
      int i = Math.abs(r.nextInt()) % 5000;
      try {
	System.err.println("Client thread "+tnum+" doing initial sleep "+i+"ms");
	Thread.currentThread().sleep(i);
      } catch (InterruptedException ie) {
	// Ignore
      }

      sock = new Socket(REMOTE_HOST, PORTNUM);
      Thread.currentThread().yield();

      if (QUIET) {
	System.err.println("Client thread "+tnum+" started");
	while (true) {
	  try {
	    Thread.currentThread().sleep(100000);
	  } catch (InterruptedException ie) {
	    // Ignore
	  }
	}
      } else {
	// Sleep so all the connections go through
	try {
	  System.err.println("Client thread "+tnum+" sleeping for 10 sec");
	  Thread.currentThread().sleep(10000);
	} catch (InterruptedException ie) {
	  // Ignore
	}
	System.err.println("Client thread "+tnum+" started");
	doBandwidth();
      }
      sock.close();
    } catch (Exception e) {
      System.err.println("Client thread "+tnum+" got exception: "+e.getMessage());
      e.printStackTrace();
    }
  }

  private void doBandwidth() throws IOException {

    OutputStream os = sock.getOutputStream();
    InputStream is = sock.getInputStream();
    byte barr[] = new byte[MESSAGE_SIZE];
    byte barr2[] = new byte[ACK_SIZE];
    int i = 0;
    long t1, t2;

    for (i = 0; i < barr.length; i++) {
      barr[i] = (byte)(i & 0xff);
    }

    System.out.println("Starting client thread "+tnum+": message size "+MESSAGE_SIZE);

    t1 = System.currentTimeMillis();
    i = 0;
    while (true) {
      if (DEBUG) System.err.println("["+i+"] Sender "+tnum+": Sending message...");
      os.write(barr); 
      os.flush();
      
      Thread.currentThread().yield();
      i++;

      if ((i % BURST_SIZE) == 0) {
        if (DEBUG) System.err.println("Sender: Receiving ack...");
        int n = 0;
        while (n < barr2.length) {
  	  n += is.read(barr2, n, (barr2.length - n));
        }

	t2 = System.currentTimeMillis();
	printResults("Client thread "+tnum, BURST_SIZE, MESSAGE_SIZE, t1, t2);
	t1 = System.currentTimeMillis();
      }
    }
  }

  public static void main(String args[]) {

    try {
      if ((args.length < 3) || (args.length > 4)) {
	System.err.println("Usage: java MultiClient [-q] <remote host> <num clients> <message size>");
	System.err.println("\t-q\tDo not send any messages (be quiet)");
	System.exit(-1);
      }

      int n = 0;
      if (args[0].equals("-q")) {
	QUIET = true;
	n++;
      } else {
	QUIET = false;
      }

      REMOTE_HOST = args[n+0];
      NUM_CONNECTIONS = Integer.valueOf(args[n+1]).intValue();
      MESSAGE_SIZE = Integer.valueOf(args[n+2]).intValue();

      if (QUIET) {
        Random r = new Random();
        int s = Math.abs(r.nextInt()) % 5000;
        try {
    	  System.err.println("Doing initial sleep "+s+"ms");
   	  Thread.currentThread().sleep(s);
    	} catch (InterruptedException ie) {
  	  // Ignore
   	}

	Socket sarr[] = new Socket[NUM_CONNECTIONS];
	for (int i = 0; i < NUM_CONNECTIONS; i++) {
	  sarr[i] = new Socket(REMOTE_HOST, PORTNUM);
          try {
   	    Thread.currentThread().sleep(10);
      	  } catch (InterruptedException ie) {
  	    // Ignore
   	  }
	}

	System.err.println("Done creating connections");
	while (true) {
	  try {
	    Thread.currentThread().sleep(100000);
	  } catch (InterruptedException ie) {
	    // Ignore
	  }
	}

      } else {
	for (int i = 0; i < NUM_CONNECTIONS; i++) {
  	  MultiClient mc = new MultiClient(i);
  	  mc.start();
   	}
      }
      
    } catch (Exception e) {
      System.out.println("MultiClient: Got exception: "+e.getMessage());
      e.printStackTrace();
    }
  }
}

