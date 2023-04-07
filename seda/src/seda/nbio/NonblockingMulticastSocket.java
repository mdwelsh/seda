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
 * Author: Rob von Behren <jrvb@cs.berkeley.edu>
 * 
 */

package seda.nbio;

import java.io.IOException;
import java.net.InetAddress;

/**
 * NonblockingMulticastSocket provides non-blocking multicast datagram I/O.
 *
 * NOTE: packets cannot be received on a socket once connect() is
 * called, due to the semantics of connect() for multicast sockets.
 * Instead, applications should generally use joinGroup(), and then
 * explicitly specify the group address as the destination in all the
 * outbound packets.
 **/
public class NonblockingMulticastSocket extends NonblockingDatagramSocket {

  InetAddress multicast_interface = null;
  int multicast_ttl = -1;

  /**
   * Create a NonblockingMulticastSocket bound to any available port. 
   */
  public NonblockingMulticastSocket() throws IOException {
    /* bind socket to any available port */
    this(0, null);
  }

  public NonblockingMulticastSocket(int port) throws IOException {
    this(port,null);
  }

  /**
   * Create a NonblockingMulticastSocket bound to the given port and
   * the given local address.
   */
  public NonblockingMulticastSocket(int port, InetAddress laddr) throws IOException {
    super(port, laddr);
  }	

  /**
   * Join a multicast group
   **/
  public void joinGroup(InetAddress addr) throws IOException {
    impl.joinGroup(addr);
  }

  /**
   * Leave a multicast group
   **/
  public void leaveGroup(InetAddress addr) throws IOException {
    impl.leaveGroup(addr);
  }

  /**
   * get the multicast ttl
   **/
  public int getTimeToLive() throws IOException {
    if(multicast_ttl == -1)
      multicast_ttl = impl.getTimeToLive();

    return multicast_ttl;
  }

  /**
   * set the time to live
   **/
  public void setTimeToLive(int ttl) throws IOException {
    multicast_ttl = ttl;
    impl.setTimeToLive(ttl);
  }

  /**
   * Get the interface associated with this multicast socket
   **/
  public InetAddress getInterface() {
    // FIXME: this is done, b/c it is easier than creating the new
    // object from w/in the Jni layer.  This is probably faster
    // anyway, so this should be OK.
    return multicast_interface;
  }

  /**
   * Set the interface associated with this socket
   **/
  public void setInterface(InetAddress addr) throws IOException {
    impl.setInterface(addr);
  }

  /**
   * This sets the state of the IP_MULTICAST_LOOP option on the
   * underlying socket.  If state==true, the socket will receive
   * packets it sends out.  If false, it will not.<p>
   *
   * NOTE: The behavior of this is somewhat strange for two multicast
   * listeners on the same physical machine.  Ideally, this should be
   * an incoming filter - each socket should throw out packets that it
   * sent out, and not deliver them to the application.<p>
   * 
   * Unfortunately, this instead seems to be an outbound filter - all
   * packets sent out on a socket with IP_MULTICAST_LOOP turned off
   * will be invisible to all sockets on the local machine -
   * regardless of whether or not these other sockets have specified
   * IP_MULTICAST_LOOP=false.
   **/
  public void seeLocalMessages(boolean state) throws IOException {
    impl.seeLocalMessages(state);
  }

}




