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

package seda.nbio;

import java.io.IOException;
import java.net.*;

/**
 * A NonblockingDatagramSocket provides non-blocking UDP (datagram) I/O.
 */
public class NonblockingDatagramSocket extends DatagramSocket 
  implements Selectable {

  NonblockingSocketImpl impl;
  boolean is_connected;

  static {
    NonblockingSocket.loadNativeLibrary();
  }

  /**
   * Create a NonblockingDatagramSocket bound to any available port. 
   */
  public NonblockingDatagramSocket() throws IOException {
    /* bind socket to any available port */
    this(0, null);
  }

  /**
   * Create a NonblockingDatagramSocket bound to the given port.
   */
  public NonblockingDatagramSocket(int port) throws IOException {
    this(port, null);
  }

  /**
   * Create a NonblockingDatagramSocket bound to the given port and
   * the given local address.
   */
  public NonblockingDatagramSocket(int port, InetAddress laddr) 
    throws IOException {

    if (port < 0 || port > 0xFFFF) { 
      throw new IllegalArgumentException("port out range: "+port); 
    }

    impl = new NonblockingSocketImpl();

    try {
      is_connected = false;
      impl.create(false);
      // 'null' bind address means INADDR_ANY
      impl.bind(laddr, port);
    } catch (IOException e) {
      close();
      throw e;
    }
  }	

  /**
   * Close this NonblockingDatagramSocket.
   */
  public synchronized void close() {
    try {
      impl.close();
    } catch (IOException e) {
      // silently ignore exceptions to mimic java's close() for dgrams
    }
  }
   
  /** 
   * Connect this NonblockingDatagramSocket to the given address and port. 
   * All send() operations with a NULL 'sendTo' address will now send
   * to this address by default. You may call connect() multiple times
   * on a NonblockingDatagramSocket to change the default send address.
   */
  public void connect(InetAddress address, int port) 
    throws IllegalArgumentException {
    if (port < 0 || port > 0xFFFF) { 
      throw new IllegalArgumentException("port out range:"+port); 
    }

    try {
      impl.connect(address, port);
    } catch (IOException e) {
      System.err.println("WARNING: NonblockingDatagramSocket.connect() - connect failed, exception is "+e);
      close();
      //silently disgard exceptions
    }
  }

  /* Attempt to connect ot the given host and port, blocking until
   * the connection is done
   */
  public void connect(String host, int port) throws UnknownHostException { 
    connect(InetAddress.getByName(host), port);
  }

  /* Disconnects a connected datagram socket. Does nothing if the
   * socket isn't already connected.
   */
  public synchronized void disconnect() {
    if(is_connected) {
      try {
	impl.disconnect();
      } catch (IOException e) {
	// XXX Ignore
      }
      is_connected = false;
    }
  }

  /**
   * Return the remote address to which this socket is bound.
   * Should be null if the socket hasn't been connected
   * to anything.
   */
  public InetAddress getInetAddress() {
    if(is_connected) {
      return impl.getInetAddress();
    } else {
      return null;
    }
  }

  /**
   * Return the local address to which this socket is bound.
   */
  public InetAddress getLocalAddress() {
    try {
      return InetAddress.getLocalHost();
    } catch (Exception e) {
      return null; // XXX MDW - Not quite right
    }
  }

  /**
   * Return the remote port to which this socket is bound.
   */
  public int getPort() {
    return impl.getPort();
  }

  /**
   * Return the local port to which this socket is bound.
   */
  public int getLocalPort() {
    return impl.getLocalPort();
  }


  /**
   * Receive a datagram from this socket. When this method returns, the 
   * DatagramPacket's buffer is filled with the data received. The datagram 
   * packet also contains the sender's IP address, and the port number 
   * on the sender's machine. 
   *
   * This method does not block if a datagram is not ready to be received. 
   * The length field of the datagram packet object contains the length 
   * of the received message, or is set to 0 if no packet was received. 
   * If the message is longer than the packet's length, the message is 
   * truncated. 
   *
   * @return The size of the received packet, or 0 if no data was received.
   */
  public int nbReceive(DatagramPacket p) throws IOException {
    return impl.receive(p);
  }

  /**
   * Receive a datagram from this socket. When this method returns, the 
   * given byte array is filled with the data received starting at the given
   * offset with the given length. If the message is longer than the given 
   * length, the message is truncated. 
   *
   * This method does not block if a datagram is not ready to be received. 
   *
   * @return The size of the received packet, or 0 if no data was received.
   */
  public int nbReceive(byte[] data, int offset, int length) throws IOException {
    DatagramPacket p = new DatagramPacket(data, offset, length);
    return nbReceive(p);
  }

  /**
   * Sends a datagram packet from this socket. The DatagramPacket includes 
   * information indicating the data to be sent, its length, the IP 
   * address of the remote host, and the port number on the remote host.  
   * This method does not block; it returns 0 if the packet could not
   * be sent.
   *
   * @return The amount of data sent, or 0 if the packet could not be sent
   * immediately.
   */
  public int nbSend(DatagramPacket p) throws IOException {
    /* Java doesn't allow you to send pkts on a connected datagram
     * socket to a different address, so mimic that
     */
    if(is_connected) {
      if( (p.getAddress() != null) && !p.getAddress().equals(impl.getInetAddress()) ) {
	throw new IllegalArgumentException("DatagramPacket address does not equal address of connected NonblockingDatagramSocket");
      }
    }
    return impl.send(p);
  }

  /**
   * Sends a datagram packet from this socket. This method constructs
   * a temporary DatagramPacket from the given data, offset, length,
   * address, and port, and calls nbSend(DatagramPacket p).
   * This method does not block; it returns 0 if the packet could not
   * be sent.
   *
   * @return The amount of data sent, or 0 if the packet could not be sent
   * immediately.
   */
  public int nbSend(byte[] data, int offset, int length, InetAddress addr, int port) throws IOException {
    return nbSend(new DatagramPacket(data, offset, length, addr, port));
  }

  /**
   * Sends a datagram packet from this socket. This method constructs
   * a temporary DatagramPacket from the given data, offset, and length.
   * This method may only be called on a connected socket; if called
   * on an unconnected socket, an IllegalArgumentException is thrown.
   * This method does not block; it returns 0 if the packet could not
   * be sent.
   *
   * @return The amount of data sent, or 0 if the packet could not be sent
   * immediately.
   */
  public int nbSend(byte[] data, int offset, int length) throws IOException {
    return nbSend(new DatagramPacket(data, offset, length));
  }

  public int getReceiveBufferSize() throws SocketException {
    throw new IllegalArgumentException("Not yet supported");
  }

  public int getSendBufferSize() throws SocketException {
    throw new IllegalArgumentException("Not yet supported");
  }

  public void setReceiveBufferSize(int size) throws SocketException {
  }

  public void setSendBufferSize(int size) throws SocketException {
    throw new IllegalArgumentException("Not yet supported");
  }

  /**
   * This method is provided for convenience and mimics blocking behavior
   * by invoking the nonblocking nbSend() operation. If the packet could
   * not be immediately sent it is simply dropped (because this is a UDP
   * socket this behavior is consistent with the lack of reliability in UDP).
   *
   * <p>Use of this method is not recommended and is provided only
   * for compatibility with java.net.DatagramSocket.
   */
  public void send(DatagramPacket p) throws IOException {
    nbSend(p);
  }

  /**
   * This method is provided for convenience and mimics blocking behavior
   * by invoking the nonblocking nbReceive() operation. If no packet could
   * be immediately received it returns immediately with a received packet
   * length of 0. 
   * 
   * <p>Use of this method is not recommended and is provided only
   * for compatibility with java.net.DatagramSocket.
   */
  public void receive(DatagramPacket p) throws IOException {
    nbReceive(p);
  }

}

