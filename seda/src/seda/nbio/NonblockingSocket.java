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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * A NonblockingSocket is a socket which exports nonblocking input/output
 * streams. It is otherwise idential to a standard socket.
 *
 * Socket connection can be either blocking or nonblocking. Use of the
 * standard constructors causes the current thread to block until 
 * the connection is established. Otherwise, there are 3 ways to check
 * if the connection has been established: 
 * (1) Call <code>finishConnection</code>,
 * (2) Call <code>connectDone</code>, or 
 * (3) Create a <code>SelectSet</code> and select on the event 
 * <code>Selectable.CONNECT_READY</code>, then call <code>connectDone</code>.
 *
 * @see SelectSet
 */
public class NonblockingSocket extends Socket implements Selectable {

  NonblockingSocketImpl impl;
  boolean is_connected;

  static boolean nativeLibraryLoaded = false;
  static Object nativeLibraryLoadLock = new Object();
  static void loadNativeLibrary() {
    synchronized (nativeLibraryLoadLock) {
      if (!nativeLibraryLoaded) {
	try {
	  System.loadLibrary("NBIO");
	  nativeLibraryLoaded = true;
	} catch (Exception e) {
	  System.err.println("Cannot load NBIO shared library");
	}
      }
    }
  }

  static {
    loadNativeLibrary();
  }

  /* Used only by NonblockingServerSocket to create an instance for accept */
  NonblockingSocket() throws IOException {
    impl = new NonblockingSocketImpl();
  }

  /**
   * Create a NonblockingSocket connection to the given host and port number.
   * This will block until the connection is established.
   */
  public NonblockingSocket(String host, int port) throws UnknownHostException, IOException {
    this(InetAddress.getByName(host), port, true);
  }

  /** 
   * Create a NonblockingSocket connection to the given host and port number.
   * If 'block' is true, block until the connection is done.
   */
  public NonblockingSocket(String host, int port, boolean block) throws UnknownHostException, IOException {
    this(InetAddress.getByName(host), port, block);
  }

  /** 
   * Create a NonblockingSocket connection to the given host and port number.
   * This will block until the connection is established.
   */
  public NonblockingSocket(InetAddress address, int port) throws UnknownHostException, IOException {
    this(address, port, true);
  }

  /**
   * Create a NonblockingSocket connection to the given host and port number.
   * If 'block' is true, block until the connection is done.
   */
  public NonblockingSocket(InetAddress address, int port, boolean block) throws IOException {
    impl = new NonblockingSocketImpl();

    if (port < 0 || port > 0xFFFF) { 
      throw new IllegalArgumentException("port out range:"+port); 
    }

    try {
      is_connected = false;
      impl.create(true);
      // 'null' bind address means INADDR_ANY
      impl.bind(null, 0);
      impl.connect(address, port);
      if (block) finishConnect(-1);
    } catch (IOException e) {
      impl.close();
      throw e;
    }
  }

  /** 
   * Block until the connection on this socket has been established.
   * 'timeout' specifies the maximum number of milliseconds to block.
   * A timeout of zero indicates no blocking (in which case this call
   * is equivalent to <code>connectDone</code>). A timeout of -1
   * causes this call to block indefinitely until the connection is 
   * established.
   * 
   * @return true is the connection was established, false if still pending. 
   */ 
  public boolean finishConnect(int timeout) throws SocketException {
    if (timeout != 0) {
      SelectSet selset = new SelectSet();
      SelectItem selitem = new SelectItem(this, Selectable.CONNECT_READY);
      selset.add(selitem);
      int r = selset.select(timeout);
      if (r == 0) {
        return connectDone();
      } else if (!connectDone()) {
        // This should never happen -- connectDone() will throw an exception
        // if there was an error
        throw new SocketException("Socket connection not completed after select! This is a bug - please e-mail mdw@cs.berkeley.edu");
      }
      return true;
    } else {
      return connectDone();
    }
  }

  /** 
   * Indicate whether the connection on this socket has been established.
   * Throws an exception if an error occurred trying to connect.
   */
  public boolean connectDone() throws SocketException {
    if (is_connected) return true;

    if (impl.connectDone()) {
      is_connected = true;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Return the remote address to which this socket is bound.
   */
  public InetAddress getInetAddress() {
    return impl.getInetAddress();
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
   * Return an InputStream from which data on this socket can be read.
   * The returned InputStream is actually a NonblockingInputStream
   * and provides nonblocking semantics.
   */
  public InputStream getInputStream() throws IOException {
    return impl.getInputStream();
  }

  /**
   * Return an OutputStream to which data on this socket can be written.
   * The returned OutputStream is actually a NonblockingOutputStream
   * and provides nonblocking semantics.
   */
  public OutputStream getOutputStream() throws IOException {
    return impl.getOutputStream();
  }

  /**
   * Currently unimplemented.
   */
  public void setTcpNoDelay(boolean on) throws SocketException {
    // XXX MDW Do nothing
  }

  /**
   * Currently unimplemented.
   */
  public boolean getTcpNoDelay() throws SocketException {
    // XXX MDW Do nothing
    return false;
  }

  /**
   * Currently unimplemented.
   */
  public void setSoLinger(boolean on, int val) throws SocketException {
    // XXX MDW Do nothing
  }

  /**
   * Currently unimplemented.
   */
  public int getSoLinger() throws SocketException {
    // XXX MDW Do nothing
    return -1;
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
   * Closes the socket.
   */
  public synchronized void close() throws IOException {
    impl.close();
  }

  public String toString() {
    return "NonblockingSocket[addr="+impl.getInetAddress().getHostAddress()+",port="+impl.getPort()+",localport="+impl.getLocalPort()+"]";
  }

}
