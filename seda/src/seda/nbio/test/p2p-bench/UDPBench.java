/* 
 * Copyright (c) 2001 by The Regents of the University of California. 
 * All rights reserved.
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
 * Author: Jerrold Smith <jjsmith@pasteur.eecs.berkeley.edu>
 * 
 */

import seda.nbio.*;

import java.io.IOException;
import java.net.*;

/**
 * This is a UDP communication benchmark based on TCPBench.java.
 * It makes use of either blocking or nonblocking datagram sockets,
 * both with and without select(). It uses specific packet sizes
 * and sends bursts of packets in the hope that at least some packets
 * in the burst will be received, considering that UDP is unreliable.
 *
 * However, not all combinations of packet and burst sizes may work,
 * as no attempt to retransmit data is made. This is meant more as a 
 * low-level benchmark and demonstration of the NonblockingDatagramSocket
 * class than anything else.
 */
public class UDPBench {

  private static final boolean DEBUG = false;  /* Pingpong */
  private static final boolean DEBUG2 = false; /* Bandwidth */
  private static final int PORTNUM = 10987;
  private static final int SELECT_TIMEOUT = 10000;
  private static final int ACK_PONG = 3;
  private static final int ACK_BAND = 4;
  private static final int PACKET_BURST_SIZE = 10;

  private static boolean sending;
  private static boolean nonblocking;
  private static boolean useselect;
  private static int NUM_MESSAGES;
  private static int MESSAGE_SIZE;
  private static InetAddress REMADDR;
  private static DatagramSocket sock; 
  private static SelectSet read_selset, write_selset;
  private static SelectItem read_selitem, write_selitem;

  private static void printResults(String msg, int numiters, int message_size, long t1, long t2) {
    double usec = (t2-t1)*1.0e3;
    double usecper = (usec/numiters);
    double megabits = (message_size*numiters*8.0)/(1024*1024);
    double bw = (megabits * 1.0e3)/(t2-t1);

    System.out.println(msg+":");
    System.out.println("\t"+numiters+" "+message_size+"-byte messages in "+usec+" usec, or "+usecper+" usec/iter.");
    System.out.println("\t"+bw+" megabits/sec.");
  }

  private static int getInt(byte[] data) {
    int sum = 0;
    if(data.length < 4) return -1;
    sum = (0xff000000 & (int)(data[3] << 24)) | 
      (0x00ff0000 & (int)(data[2] << 16)) |
      (0x0000ff00 & (int)(data[1] << 8)) |
      (0x000000ff & (int)(data[0] << 0));
    return sum;
  }

  private static void putInt(byte[] data, int p) {
    if(data.length < 4) return;
    data[3] = (byte)((0xff000000 & p) >>24);
    data[2] = (byte)((0x00ff0000 & p) >>16);
    data[1] = (byte)((0x0000ff00 & p) >>8);
    data[0] = (byte)((0x000000ff & p) >>0);
  }

  // Use a TCP connection to synchronize the sender and receiver at
  // startup.
  private static void synchConnection() {
    ServerSocket serv = null;
    Socket tsock;

    try {
      if(sending) {
	tsock = new Socket(REMADDR, PORTNUM);
	tsock.close();
      } else {
	serv = new ServerSocket(PORTNUM);
	tsock = serv.accept();
	serv.close();
	tsock.close();
      }
    } catch(IOException e) {
      System.err.println("caught in synchConnections: "+e);
    }
  }

  private static void doPingpong() throws IOException {
    NonblockingDatagramSocket nbsock = null;
    byte barr[] = new byte[MESSAGE_SIZE];
    byte barr2[] = new byte[MESSAGE_SIZE];
    byte back[] = new byte[ACK_PONG];
    int i;
    int received=0, seqnum=0;
    long t1, t2;
    
    if (nonblocking) {
      nbsock = (NonblockingDatagramSocket)sock;
    }
    
    for(int k=0; k<ACK_PONG; k++) {
      back[k] = (byte)(k & 0xff);
    }
    for (i = 0; i < barr.length; i++) {
      barr[i] = (byte)(i & 0xff);
    }
    
    System.err.println("Starting pingpong test: message size "+MESSAGE_SIZE+", num messages "+NUM_MESSAGES+", nonblocking="+nonblocking+", useselect="+useselect);
    
    synchConnection();  //so that we don't start timing too early

    if (sending) {
      t1 = System.currentTimeMillis();
      for (i = 0; i < NUM_MESSAGES; i++) {
        if (DEBUG) System.err.println("["+i+"] Sender: Sending message "+seqnum+"...");
	//put the sequence number in the array
	putInt(barr, seqnum);
	if (nonblocking) {
	  for(int k=0; k<PACKET_BURST_SIZE; k++) {
	    if (useselect) {
	      while ((write_selitem.revents & Selectable.WRITE_READY) == 0) {
		write_selset.select(SELECT_TIMEOUT);
	      }
	      if (DEBUG) System.err.println("["+i+"] Sender: Select says write is ready");
	      write_selitem.revents = 0;
	    }
	    DatagramPacket p = new DatagramPacket(barr, MESSAGE_SIZE, REMADDR, PORTNUM);
	    nbsock.nbSend(p);
	  }
        } else {
	  /* Blocking */
	  DatagramPacket p = new DatagramPacket(barr, MESSAGE_SIZE, REMADDR, PORTNUM);
	  for(int k=0; k<PACKET_BURST_SIZE; k++) {
	    sock.send(p);
	  }
	}
	seqnum++;
        if (DEBUG) System.err.println("["+i+"] Sender: Waiting for message "+seqnum+"...");
	DatagramPacket p = new DatagramPacket(barr2, MESSAGE_SIZE);
	while(true) {
	  //keep looping until we get the next expected pkt: ORANGE UNSAFE, should put in a timer... later
	  if (nonblocking) {
	    if (useselect) {
	      while ((read_selitem.revents & Selectable.READ_READY) == 0) {
		read_selset.select(SELECT_TIMEOUT);
	      }
	      read_selitem.revents = 0;
	    }
	    p.setLength(MESSAGE_SIZE);
	    nbsock.nbReceive(p);
	  } else {
	    /* Blocking */
	    sock.receive(p);
	  }

	  if (DEBUG) System.err.println("["+i+"] Sender: Got packet of size "+p.getLength());

	  if(p.getLength() == ACK_PONG) {
	    //we're done?? this shouldn't happen!!
	    throw new IOException("sender received an ack!!!");
	  } else if (p.getLength() == ACK_BAND) {
	    //wtf?? got a bandwidth test pkt ack??
	    throw new IOException("got an ack_band in sender in pong!!");
	  } else if (p.getLength() != 0) {
	    int ret = getInt(barr2);
	    if(ret < 0) {
	      throw new IOException("bad int in an array, it's negative!!");
	    } else if(ret == seqnum) {
	      //got the next expected pkt
	      seqnum++;
	      received++;
	      break;
	    } else if(ret < seqnum) {
	      //getting a repeated pkt, keep looping until find expected pkt
	      received++;
	    } else {
	      //out of sync!!! got a larger number than we expect, bad, right?
	      // even if all in a volley get dropped, just means the other side
	      // should loop forever, right now it doesn't advance the counter
	      // and continue.
	      // ORANGE: change this if add in timers to make this program safe
	      throw new IOException("expecting seqnum: "+seqnum+", but received: "+ret);
	    }
	  }
	} //while(true)
      } // for (i = 0; i < NUM_MESSAGES; i++)

      //just to make sure other side knows we're done
      DatagramPacket sent = new DatagramPacket(back, ACK_PONG, REMADDR, PORTNUM);
      for(int k=0; k<PACKET_BURST_SIZE; k++) {
	sock.send(sent);
      }

      t2 = System.currentTimeMillis();    
      printResults("Pingpong test", received, MESSAGE_SIZE, t1, t2);

    } else {
      /* Receiving */
      i = 0;
      t1 = System.currentTimeMillis();
      DatagramPacket p = new DatagramPacket(barr2, MESSAGE_SIZE);

    receiving:
      while(true) { //rely on sender sending a sentinel 

	if (DEBUG) System.err.println("["+i+"] Receiver: expecting message "+seqnum+"...");
	while(true) { //looking for a particular seqnum

	  if (nonblocking) {
	    if (useselect) {
	      while ((read_selitem.revents & Selectable.READ_READY) == 0) {
		read_selset.select(SELECT_TIMEOUT);
	      }
	      if (DEBUG) System.err.println("["+i+"] Receiver: Select says read ready");
	      read_selitem.revents = 0;
	    }
	    p.setLength(MESSAGE_SIZE);
	    nbsock.nbReceive(p);
	  } else {
	    /* Blocking */
	    sock.receive(p);
 	  }

	  if (DEBUG) System.err.println("["+i+"] Receiver: Got packet of size "+p.getLength());

	  if(p.getLength() == ACK_PONG) {
	    //we're done dude
	    break receiving;
	  } else if (p.getLength() == ACK_BAND) {
	    //wtf??
	    throw new IOException("got an ack_band in receiver in ping pong test");
	  } else if (p.getLength() != 0) {
	    int ret = getInt(barr2);
	    if(ret < 0) {
	      throw new IOException("bad int in an array: it's negative!!");
	    } else if(ret == seqnum) {
	      //got the pkt we're expecting
	      seqnum++;
	      i++;
	      received++;
	      break;
	    } else if(ret < seqnum) {
	      //getting a repeated pkt, keep looping
	      received++;
	    } else {
	      //out of sync, got a larger number than needed
	      // right now impossible to get to this case (or at least if it happens, something bad happened
	      //ORANGE: deal with this case if put in timers to avoid infinite loops if all
	      // pkts in a volley are droppped
	      throw new IOException("expecting pkt: "+seqnum+", but received: "+ret);
	    }
	  }

	}

	if (DEBUG) System.err.println("["+i+"] Receiver: Sending message "+seqnum+"...");
	putInt(barr, seqnum);

	if (nonblocking) {
	  for(int k=0; k<PACKET_BURST_SIZE; k++) {
	    if (useselect) {
	      while ((write_selitem.revents & Selectable.WRITE_READY) == 0) {
		write_selset.select(SELECT_TIMEOUT);
	      }
	      write_selitem.revents = 0;
	    }
	    DatagramPacket q = new DatagramPacket(barr, 0, barr.length, REMADDR, PORTNUM);
	    nbsock.nbSend(q);
	  }
	} else {
	  /* Blocking */
	  DatagramPacket q = new DatagramPacket(barr, MESSAGE_SIZE, REMADDR, PORTNUM);
	  for(int k=0; k<PACKET_BURST_SIZE; k++) {
	    sock.send(q);
	  }
	}
	seqnum++;
      } //outer while(true) that loops until get sentinel

      t2 = System.currentTimeMillis();
      printResults("Pingpong test", received, MESSAGE_SIZE, t1, t2);
    }
  }  

  /* what doBandwidth does:
   * sender starts off, sends NUM_MESSAGES packets to the receiver, then sends
   * a burst of sentinel packets, and then waits for an ack (another 
   * sentinel packet) from the receiver. the receiver starts listening for 
   * any packets. 
   * it's possible for some packets to be floating around from pingpong, so
   * it's important to change the size of MESSAGE_SIZE to be something different
   * (in main, i do a +1). if received pkt.size == MESSAGE_SIZE, increase our
   * counter, if pkt.size == ACK_BAND, then the sender is done sending, so
   * fire off a volley of ACK_BAND sized packets back at the sender as an ack.
   * any other size is probably some remnant from the ping pong test, so just
   * ignore it.
   */
  private static void doBandwidth() throws IOException {
    NonblockingDatagramSocket nbsock = null;

    byte barr[] = new byte[MESSAGE_SIZE];
    byte barr2[] = new byte[ACK_BAND];
    int i;
    long t1, t2;

    if (nonblocking) {
      nbsock = (NonblockingDatagramSocket)sock;
    }

    for (i = 0; i < barr.length; i++) {
      barr[i] = (byte)(i & 0xff);
    }

    System.err.println("Starting bandwidth test: message size "+MESSAGE_SIZE+", num messages "+NUM_MESSAGES);

    if (sending) {
      t1 = System.currentTimeMillis();
      for (i = 0; i < NUM_MESSAGES; i++) {
	DatagramPacket p = new DatagramPacket(barr, MESSAGE_SIZE, REMADDR, PORTNUM);
        if (DEBUG2) System.err.println("["+i+"] Sender: Sending message...");
	if (nonblocking) {
	  if (useselect) {
	    while ((write_selitem.revents & Selectable.WRITE_READY) == 0) {
	      write_selset.select(SELECT_TIMEOUT);
	    }
	    write_selitem.revents = 0;
	  }
	  nbsock.nbSend(p);
        } else {
	  /* Blocking */
	  sock.send(p);
	}
      }
      if (DEBUG) System.err.println("Sender: Sending sentinel packets....");

      // hope that PACKET_BURST_SIZE packets will do it
      for(i=0; i<PACKET_BURST_SIZE; i++) {
	DatagramPacket sent = new DatagramPacket(barr2, ACK_BAND, REMADDR, PORTNUM);
	if (nonblocking) {
	  if (useselect) {
            while ((write_selitem.revents & Selectable.WRITE_READY) == 0)  {
              write_selset.select(SELECT_TIMEOUT);
            }
	    write_selitem.revents = 0;
          }
	  nbsock.nbSend(sent);
	} else {
	  sock.send(sent);
	}
      }

      if (DEBUG) System.err.println("Sender: Listening for ack....");
      while(true) {
	DatagramPacket p = new DatagramPacket(barr, MESSAGE_SIZE);
	if (nonblocking) {
	  if (useselect) {
            while ((read_selitem.revents & Selectable.READ_READY) == 0)  {
              read_selset.select(SELECT_TIMEOUT);
            }
	    read_selitem.revents = 0;
          }
	  p.setLength(MESSAGE_SIZE);
	  nbsock.nbReceive(p);
	} else {
	  /* Blocking */
	  sock.receive(p);
	}
	//got confirmation, so stop
	if(p.getLength() == ACK_BAND) break;
      }
      t2 = System.currentTimeMillis();
      printResults("Bandwidth test", NUM_MESSAGES, MESSAGE_SIZE, t1, t2);
    } else {
      /* Receiving */
      int received = 0;  //counter that keeps track of actual # received

      if (DEBUG2) System.err.println("["+i+"] Receiver: Receiving message...");
      t1 = System.currentTimeMillis();
      while(true) {
	DatagramPacket p = new DatagramPacket(barr, MESSAGE_SIZE);
        if (nonblocking) {
	  if (useselect) {
	    while ((read_selitem.revents & Selectable.READ_READY) == 0) {
	      read_selset.select(SELECT_TIMEOUT);
	    }
	    read_selitem.revents = 0;
	  }
	  p.setLength(MESSAGE_SIZE);
	  nbsock.nbReceive(p);
	} else {
          /* Blocking */
	  sock.receive(p);
        }

	if(p.getLength() == ACK_BAND) break;
	else if(p.getLength() == MESSAGE_SIZE) received++;
      }

      if (DEBUG) System.err.println("Receiver: Sending ack...");

      for(int k=0; k<PACKET_BURST_SIZE; k++) {
	DatagramPacket p = new DatagramPacket(barr2, ACK_BAND, REMADDR, PORTNUM);
	if (nonblocking) {
	  if (useselect) {
	    while ((write_selitem.revents & Selectable.WRITE_READY) == 0) {
  	      write_selset.select(SELECT_TIMEOUT);
            }
	    write_selitem.revents = 0;
	  }
	  nbsock.nbSend(p);
	} else {
	  /* Blocking */
	  sock.send(p);
	}
      }
      t2 = System.currentTimeMillis();
      printResults("Bandwidth test", received, MESSAGE_SIZE, t1, t2);
    }
  }

  public static void usage() {
    System.err.println("Usage: java UDPBench [-n] [-s] [send | recv] <remote node> <num messages> <message size>");
    System.err.println("Options:");
    System.err.println("  -n\t\tUse nonblocking sockets");
    System.err.println("  -s\t\tUse select/poll interface (implies -n)");
  }

  public static void main(String args[]) {
    ServerSocket servsock = null;
    NonblockingServerSocket nbservsock = null;

    try {

      if ((args.length < 4) || (args.length > 5)) {
	usage();
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

      REMADDR  = InetAddress.getByName(args[n+1]);
      NUM_MESSAGES = Integer.valueOf(args[n+2]).intValue();
      MESSAGE_SIZE = Integer.valueOf(args[n+3]).intValue();
      if(MESSAGE_SIZE < ACK_BAND) {
	// to make sure we don't send a sentinel packet by mistake
	MESSAGE_SIZE = ACK_BAND+1;
      }
      
      System.err.println("Connecting to "+args[n+1]+":"+PORTNUM+",  at: "+REMADDR);

      boolean connected = false;
      while (!connected) {
	try {
	  if (nonblocking) {
	      sock = new NonblockingDatagramSocket(PORTNUM);
	      ((NonblockingDatagramSocket)sock).connect(REMADDR, PORTNUM);
	  } else {
	      sock = new DatagramSocket(PORTNUM);
	      sock.connect(REMADDR, PORTNUM);
	  }

	  if (nonblocking && useselect) {
	    read_selset = new SelectSet();
	    read_selitem = new SelectItem((NonblockingDatagramSocket)sock, (short)Selectable.READ_READY);
	    read_selset.add(read_selitem);

	    write_selset = new SelectSet();
	    write_selitem = new SelectItem((NonblockingDatagramSocket)sock, (short)Selectable.WRITE_READY);
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
      System.err.println("Connected.....");
      
      doPingpong();
      MESSAGE_SIZE++;
      doBandwidth();
      sock.close();
    } catch (Exception e) {
      System.err.println("TCPBench: Got exception: "+e.getMessage());
      e.printStackTrace();
    }
  }



}

