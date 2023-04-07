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

/* This is a Java TCP communication benchmark capable of using either
 * blocking or nonblocking sockets. Since it only uses a single 
 * client-server socket, it is mainly used to measure latency and bandwidth
 * over a single connection.
 * 
 * To run:
 *
 *  On the 'server' machine, do:
 *    java TCPBench [-n] [-s] recv <remote host> <num messages> <message size>
 *  where <remote host> can be anything, <num messages> is the number of
 *  messages to send, and <message size> is the size of each message.
 *
 *  On the 'client' machine, do:
 *    java TCPBench [-n] [-s] send <remote host> <num messages> <message size>
 *  where <remote host> is the hostname of the 'server', <num messages> 
 *  is the number of messages to send, and <message size> is the size of 
 *  each message.
 *
 *  Use -n to specify nonblocking sockets, and -s to specify that the
 *  select interface should be used.
 *
 */

import seda.nbio.*;
import seda.util.Tracer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPBench {

  private static final boolean DEBUG = false;  /* Pingpong */
  private static final boolean DEBUG2 = false; /* Bandwidth */
  private static final int PORTNUM = 5957;
  private static final int SELECT_TIMEOUT = 10000;
  private static final boolean PROFILE = false;

  private static final boolean DO_PINGPONG = true;
  private static final boolean DO_BANDWIDTH = true;

  private static boolean sending;
  private static boolean nonblocking;
  private static boolean useselect;
  private static int NUM_MESSAGES;
  private static int MESSAGE_SIZE;
  private static Socket sock; 
  private static SelectSet read_selset, write_selset;
  private static SelectItem read_selitem, write_selitem;
  private static Tracer tracer;

  private static void printResults(String msg, int numiters, int message_size, long t1, long t2) {
    double usec = (t2-t1)*1.0e3;
    double usecper = (usec/numiters);
    double megabits = (message_size*numiters*8.0)/(1024*1024);
    double bw = (megabits * 1.0e3)/(t2-t1);

    System.out.println(msg+":");
    System.out.println("\t"+numiters+" "+message_size+"-byte messages in "+usec+" usec, or "+usecper+" usec/iter.");
    System.out.println("\t"+bw+" megabits/sec.");
  }

  private static void doPingpong() throws IOException {
    OutputStream os = sock.getOutputStream();
    InputStream is = sock.getInputStream();
    NonblockingOutputStream nbos = null;
    NonblockingInputStream nbis = null;
    if (nonblocking) {
      nbos = (NonblockingOutputStream)os;
      nbis = (NonblockingInputStream)is;
    }

    byte barr[] = new byte[MESSAGE_SIZE];
    byte barr2[] = new byte[MESSAGE_SIZE];
    int i;
    long t1, t2;

    for (i = 0; i < barr.length; i++) {
      barr[i] = (byte)(i & 0xff);
    }

    System.out.println("Starting pingpong test: message size "+MESSAGE_SIZE+", num messages "+NUM_MESSAGES+", nonblocking="+nonblocking+", useselect="+useselect);

    if (sending) {
      t1 = System.currentTimeMillis();
      for (i = 0; i < NUM_MESSAGES; i++) {
        if (DEBUG) System.err.println("["+i+"] Sender: Sending message...");
	if (PROFILE) tracer.trace("Sending msg");
	if (nonblocking) {
 	  int n = 0;
	  if (PROFILE) tracer.trace("  Write loop");
	  while (n < barr.length) {

            if (useselect) {
	      if (PROFILE) tracer.trace("  write select");
	      while ((write_selitem.revents & Selectable.WRITE_READY) == 0) {
  	        write_selset.select(SELECT_TIMEOUT);
              }
              if (DEBUG) System.err.println("["+i+"] Sender: Select says write is ready");
	      if (PROFILE) tracer.trace("  write select done");
	      write_selitem.revents = 0;
	    }

	    if (PROFILE) tracer.trace("  nbWrite");
	    n += nbos.nbWrite(barr, n, barr.length - n);
	    if (PROFILE) tracer.trace("  nbWrite done");
	  }
	  if (PROFILE) tracer.trace("  Write loop done");
        } else {
	  /* Blocking */
	  if (PROFILE) tracer.trace("write");
	  os.write(barr);
	  os.flush();
	  if (PROFILE) tracer.trace("write done");
	}
        if (DEBUG) System.err.println("["+i+"] Sender: Receiving message...");
	if (PROFILE) tracer.trace("Receiving msg");

	if (nonblocking) {
	  int n = 0;
	  if (PROFILE) tracer.trace("  Read loop");
	  while (n < barr2.length) {
            if (useselect) {
	      if (PROFILE) tracer.trace("  read select");
	      while ((read_selitem.revents & Selectable.READ_READY) == 0) {
                read_selset.select(SELECT_TIMEOUT);
              }
	      if (PROFILE) tracer.trace("  read select done");
	      read_selitem.revents = 0;
            }
	    if (PROFILE) tracer.trace("  nbis.read");
	    n += nbis.read(barr2, n, (barr2.length - n));
	    if (PROFILE) tracer.trace("  nbis.read done");
	  }
	  if (PROFILE) tracer.trace("  Read loop done");
	} else {
	  /* Blocking */
	  if (PROFILE) tracer.trace("  Read loop");
          int n = 0;
	  while (n < barr2.length) {
	    n += is.read(barr2, n, (barr2.length - n));
 	  }
	  if (PROFILE) tracer.trace("  Read loop done");
        }
      }
      t2 = System.currentTimeMillis();
      printResults("Pingpong test", NUM_MESSAGES, MESSAGE_SIZE, t1, t2);
    } else {
      /* Receiving */
      t1 = System.currentTimeMillis();
      for (i = 0; i < NUM_MESSAGES; i++) {
        if (DEBUG) System.err.println("["+i+"] Receiver: Receiving message...");
	if (nonblocking) {
	  int n = 0;
	  while (n < barr2.length) {
            if (useselect) {
	      while ((read_selitem.revents & Selectable.READ_READY) == 0) {
                read_selset.select(SELECT_TIMEOUT);
              }
              if (DEBUG) System.err.println("["+i+"] Receiver: Select says read ready");
	      read_selitem.revents = 0;
            }
	    n += nbis.read(barr2, n, (barr2.length - n));
  	  }
	} else {
	  /* Blocking */
          int n = 0;
	  while (n < barr2.length) {
	    n += is.read(barr2, n, (barr2.length - n));
 	  }
        }

        if (DEBUG) System.err.println("["+i+"] Receiver: Sending message...");

	if (nonblocking) {
  	  int n = 0;
	  while (n < barr.length) {

            if (useselect) {
	      while ((write_selitem.revents & Selectable.WRITE_READY) == 0) {
  	        write_selset.select(SELECT_TIMEOUT);
              }
	      write_selitem.revents = 0;
	    }

	    n += nbos.nbWrite(barr, n, barr.length - n);
	  }
        } else {
	  /* Blocking */
	  os.write(barr);
	  os.flush();
	}

      }
      t2 = System.currentTimeMillis();
      printResults("Pingpong test", NUM_MESSAGES, MESSAGE_SIZE, t1, t2);
    }
  }

  private static void doBandwidth() throws IOException {

    int ACK_SIZE = 4;

    OutputStream os = sock.getOutputStream();
    InputStream is = sock.getInputStream();
    NonblockingOutputStream nbos = null;
    NonblockingInputStream nbis = null;
    if (nonblocking) {
      nbos = (NonblockingOutputStream)os;
      nbis = (NonblockingInputStream)is;
    }

    byte barr[] = new byte[MESSAGE_SIZE];
    byte barr2[] = new byte[ACK_SIZE];
    int i;
    long t1, t2;

    for (i = 0; i < barr.length; i++) {
      barr[i] = (byte)(i & 0xff);
    }

    System.out.println("Starting bandwidth test: message size "+MESSAGE_SIZE+", num messages "+NUM_MESSAGES);

    if (sending) {
      t1 = System.currentTimeMillis();
      for (i = 0; i < NUM_MESSAGES; i++) {
        if (DEBUG2) System.err.println("["+i+"] Sender: Sending message...");
	if (nonblocking) {
  	  int n = 0;
	  while (n < barr.length) {

            if (useselect) {
	      while ((write_selitem.revents & Selectable.WRITE_READY) == 0) {
  	        write_selset.select(SELECT_TIMEOUT);
              }
	      write_selitem.revents = 0;
	    }

	    n += nbos.nbWrite(barr, n, barr.length - n);
	  }
        } else {
	  /* Blocking */
	  os.write(barr);
	  os.flush();
	}
      }
      if (DEBUG) System.err.println("Sender: Receiving ack...");

      if (nonblocking) {
        int n = 0;
        while (n < barr2.length) {
          if (useselect) {
            while ((read_selitem.revents & Selectable.READ_READY) == 0)  {
              read_selset.select(SELECT_TIMEOUT);
            }
	    read_selitem.revents = 0;
          }
	  n += nbis.read(barr2, n, (barr2.length - n));
  	}
      } else {
        /* Blocking */
        int n = 0;
        while (n < barr2.length) {
	  n += is.read(barr2, n, (barr2.length - n));
 	}
      }
      t2 = System.currentTimeMillis();
      printResults("Bandwidth test", NUM_MESSAGES, MESSAGE_SIZE, t1, t2);
    } else {
      /* Receiving */
      t1 = System.currentTimeMillis();
      for (i = 0; i < NUM_MESSAGES; i++) {
        if (DEBUG2) System.err.println("["+i+"] Receiver: Receiving message...");

        if (nonblocking) {
          int n = 0;
          while (n < barr.length) {
            if (useselect) {
              while ((read_selitem.revents & Selectable.READ_READY) == 0) {
                read_selset.select(SELECT_TIMEOUT);
              }
	      read_selitem.revents = 0;
            }
	    n += nbis.read(barr, n, (barr.length - n));
    	  }
        } else {
          /* Blocking */
          int n = 0;
          while (n < barr.length) {
	    n += is.read(barr, n, (barr.length - n));
   	  }
        }
      }

      if (DEBUG) System.err.println("Receiver: Sending ack...");
      if (nonblocking) {
  	int n = 0;
	while (n < barr2.length) {
          if (useselect) {
	    while ((write_selitem.revents & Selectable.WRITE_READY) == 0) {
  	      write_selset.select(SELECT_TIMEOUT);
            }
	    write_selitem.revents = 0;
	  }
	  n += nbos.nbWrite(barr2, n, barr2.length - n);
        }
      } else {
        /* Blocking */
	os.write(barr2);
	os.flush();
      }
      t2 = System.currentTimeMillis();
      printResults("Bandwidth test", NUM_MESSAGES, MESSAGE_SIZE, t1, t2);
    }
  }

  public static void main(String args[]) {
    String REMOTE_HOST;
    ServerSocket servsock = null;
    NonblockingServerSocket nbservsock = null;

    if (PROFILE) tracer = new Tracer("TCPBench");

    try {

      if ((args.length < 4) || (args.length > 5)) {
	System.err.println("Usage: java TCPBench [-n] [-s] [send | recv] <remote node> <num messages> <message size>");
	System.err.println("Options:");
	System.err.println("  -n\t\tUse nonblocking sockets");
	System.err.println("  -s\t\tUse select/poll interface (implies -n)");
	System.exit(-1);
      }

      sending = false;
      nonblocking = false;
      useselect = false;

      int n;
      for (n = 0; n < 2; ) {
        if (args[n].equals("-n")) {
          nonblocking = true;
	  n++;
        } else if (args[n].equals("-s")) {
	  useselect = true;
	  nonblocking = true;
	  n++;
	} else {
	  break;
	}
      }

      if (args[n].equals("send")) sending = true;

      REMOTE_HOST = args[n+1];
      NUM_MESSAGES = Integer.valueOf(args[n+2]).intValue();
      MESSAGE_SIZE = Integer.valueOf(args[n+3]).intValue();
      
      if (sending) {

        System.out.println("Connecting to "+REMOTE_HOST+":"+PORTNUM);
        boolean connected = false;
        while (!connected) {
          try {
	    if (nonblocking) sock = new NonblockingSocket(REMOTE_HOST,PORTNUM);
	    else sock = new Socket(REMOTE_HOST,PORTNUM);

	    if (nonblocking && useselect) {
	      read_selset = new SelectSet();
	      read_selitem = new SelectItem((NonblockingSocket)sock, (short)Selectable.READ_READY);
	      read_selset.add(read_selitem);

	      write_selset = new SelectSet();
	      write_selitem = new SelectItem((NonblockingSocket)sock, (short)Selectable.WRITE_READY);
	      write_selset.add(write_selitem);
	    }

	    connected = true;
          } catch (IOException e) {
          }
	  if (!connected) {
	    try {
  	      Thread.sleep(100);
            } catch (InterruptedException e) {
	    }
	  }
        }
      } else {
        /* Receiving */
	if (nonblocking) nbservsock = new NonblockingServerSocket(PORTNUM);
	else servsock = new ServerSocket(PORTNUM);
	System.out.println("Waiting for connection...");
	if (nonblocking) sock = nbservsock.accept();
	else sock = servsock.accept();

	if (nonblocking && useselect) {
	  read_selset = new SelectSet();
     	  read_selitem = new SelectItem((NonblockingSocket)sock, (short)Selectable.READ_READY);
	  read_selset.add(read_selitem);

      	  write_selset = new SelectSet();
	  write_selitem = new SelectItem((NonblockingSocket)sock, (short)Selectable.WRITE_READY);
	  write_selset.add(write_selitem);
	}

	System.out.println("Got connection from "+sock.getInetAddress().toString());
      }

      if (DO_PINGPONG) doPingpong();
      if (DO_BANDWIDTH) doBandwidth();
      sock.close();
      if (!sending) {
        if (nonblocking) nbservsock.close();
        else servsock.close();
      }
      if (PROFILE) Tracer.dumpAll();

    } catch (Exception e) {
      System.out.println("TCPBench: Got exception: "+e.getMessage());
      e.printStackTrace();
    }
  }
}

