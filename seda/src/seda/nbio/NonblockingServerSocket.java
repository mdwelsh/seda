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

package seda.nbio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * A NonblockingServerSocket implements a nonblocking variant of
 * java.net.ServerSocket. (Ideally it would simply extend the latter
 * class, but ServerSocket does not contain an appropriate public
 * constructor which would make that feasible.)
 *
 * @see java.net.ServerSocket
 */
public class NonblockingServerSocket implements Selectable {

  private static final boolean DEBUG = false;

  // Default value stolen from Apache :-)
  private static final int DEFAULT_LISTEN_BACKLOG = 511;

  static {
    NonblockingSocket.loadNativeLibrary();
  }

  NonblockingSocketImpl impl;

  private NonblockingSocket accept_tmp = null;

  /**
   * Create a nonblocking server socket listening on the given port.
   */
  public NonblockingServerSocket(int port) throws IOException {
    this(port, DEFAULT_LISTEN_BACKLOG, null);
  }

  /**
   * Create a nonblocking server socket listening on the given port with
   * the given connection backlog (the default is 511).
   */
  public NonblockingServerSocket(int port, int backlog) throws IOException {
    this(port, backlog, null);
  }

  /** 
   * Create a nonblocking server socket listening on the given port, 
   * with the given connection backlog, bound to the given address.
   * This is useful if you wish to bind the socket to an address other
   * than INADDR_ANY.
   */
  public NonblockingServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
    impl = new NonblockingSocketImpl();

    if (port < 0 || port > 0xFFFF) { 
      throw new IllegalArgumentException("port out range:"+port); 
    }

    try {
      if (DEBUG) System.err.println("NonblockingServerSocket (port "+port+"): Calling create");
      impl.create(true);
      if (DEBUG) System.err.println("NonblockingServerSocket (port "+port+"): Calling bind");
      impl.bind(bindAddr, port);
      if (DEBUG) System.err.println("NonblockingServerSocket (port "+port+"): Calling listen");
      impl.listen(backlog);
    } catch (IOException e) {
      impl.close();
      throw e;
    }
    if (DEBUG) System.err.println("NonblockingServerSocket (port "+port+"): Returning from constructor");
  }

  /**
   * Accept a connection on this server socket. This is a <b>blocking</b>
   * operation. 
   *
   * @return A NonblockingSocket corresponding to the new connection.
   */
  public NonblockingSocket accept() throws IOException {
    NonblockingSocket s = new NonblockingSocket();
    if (DEBUG) System.err.println("NonblockingServerSocket: Calling blocking accept");
    impl.accept(s.impl);
    if (DEBUG) System.err.println("NonblockingServerSocket: Returned from blocking accept");
    return s;
  }

  /**
   * Perform a nonblocking accept() on this socket. Returns null if no
   * connection was established. Selecting this socket for ACCEPT_READY
   * will allow you to determine if nbAccept() will return a new connection.
   *
   * @see SelectSet
   */
  public synchronized NonblockingSocket nbAccept() throws IOException {
    if (DEBUG) System.err.println("NonblockingServerSocket: Calling nonblocking accept");
    if (accept_tmp == null) accept_tmp = new NonblockingSocket();
    if (impl.nbAccept(accept_tmp.impl) < 0) {
      if (DEBUG) System.err.println("NonblockingServerSocket: Nonblocking accept returning null");
      return null;
    } else {
      NonblockingSocket tmp = accept_tmp;
      accept_tmp = null;
      if (DEBUG) System.err.println("NonblockingServerSocket: Nonblocking accept returning "+tmp);
      return tmp;
    }
  }

  /** 
   * Return the address to which this socket is bound.
   */
  public InetAddress getInetAddress() {
    return impl.getInetAddress();
  }

  /**
   * Return the port to which this socket is bound.
   */
  public int getLocalPort() {
    return impl.getLocalPort();
  }

  /**
   * Currently unimplemented.
   */
  public synchronized void setSoTimeout(int timeout) throws SocketException {
    // XXX MDW Do nothing
  }

  /**
   * Currently unimplemented.
   */
  public synchronized int getSoTimeout() throws SocketException {
    // XXX MDW Do nothing
    return 0;
  }

  /**
   * Close the socket.
   */
  public synchronized void close() throws IOException {
    impl.close();
  }

  public String toString() {
    String s;
    try {
      s = impl.getInetAddress().getHostAddress();
    } catch (NullPointerException e) {
      s = "<none>";
    }
    return "NonblockingServerSocket[addr="+s+",localport="+impl.getLocalPort()+"]";
  }

}
