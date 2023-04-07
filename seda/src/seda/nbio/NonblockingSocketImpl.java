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

package seda.nbio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

class NonblockingSocketImpl {

  private NBIOFileDescriptor fd;
  private InetAddress address;
  private int port;
  private int localport;

  private native void nbSocketCreate(boolean stream);
  private native void nbSocketConnect(InetAddress address, int port) throws IOException;
  private native boolean nbSocketConnectDone() throws SocketException;
  private native void nbSocketBind(InetAddress address, int port) throws IOException;
  private native void nbSocketListen(int count) throws IOException;
  private native int nbSocketAccept(NonblockingSocketImpl s, boolean block) throws IOException;
  private native int nbSocketAvailable() throws IOException;
  private native void nbSocketClose() throws IOException;
  private native int nbSendTo(DatagramPacket p) throws IOException;
  private native int nbReceive(DatagramPacket p) throws IOException;
  private native void nbDisconnect() throws SocketException;

  // Multicast support
  private native void nbJoinGroup(InetAddress address) throws IOException;
  private native void nbLeaveGroup(InetAddress address) throws IOException;
  private native void nbSetTimeToLive(int ttl) throws IOException;
  private native int nbGetTimeToLive() throws IOException;
  private native void nbSetInterface(InetAddress address) throws IOException;
  private native void nbSeeLocalMessages(boolean state) throws IOException;
  
  
  NonblockingSocketImpl() {
    fd = new NBIOFileDescriptor();
  }

  NonblockingSocketImpl(InetAddress address) {
    this();
    this.address = address;
  }

  protected void create(boolean stream) throws IOException {
    nbSocketCreate(stream);
  }

  protected void connect(String host, int port) throws IOException {
    try {
      InetAddress address = InetAddress.getByName(host);
      this.address = address;
      this.port = port;
      try {
        nbSocketConnect(address,port);
	return;
      } catch (IOException e) {
        close();
	throw e;
      }
    } catch (UnknownHostException e) {
      close();
      throw e;
    }
  }

  protected void connect(InetAddress address, int port) throws IOException {
    this.address = address;
    this.port = port;
    try {
      nbSocketConnect(address,port);
    } catch (IOException e) {
      close();
      throw e;
    }
  }

  protected boolean connectDone() throws SocketException {
    return nbSocketConnectDone();
  }

  protected void bind(InetAddress host, int port) throws IOException {
    nbSocketBind(host, port);
  }

  protected void listen(int backlog) throws IOException {
    nbSocketListen(backlog);
  }

  protected void accept(NonblockingSocketImpl s) throws IOException {
    if (nbSocketAccept(s, true) < 0) throw new IOException("Blocking accept() returned error");
  }

  protected int nbAccept(NonblockingSocketImpl s) throws IOException {
    return nbSocketAccept(s, false);
  }

  protected InputStream getInputStream() throws IOException {
    return new NonblockingSocketInputStream(this);
  }

  protected OutputStream getOutputStream() throws IOException {
    return new NonblockingSocketOutputStream(this);
  }

  protected int available() throws IOException {
    return nbSocketAvailable();
  }

  protected void close() throws IOException {
    if (fd != null) {
      nbSocketClose();
      fd = null;
    }
  }

  protected void finalize() throws IOException {
    close();
  }

  protected InetAddress getInetAddress() {
    return address;
  }

  protected int getPort() {
    return port;
  }

  protected int getLocalPort() {
    return localport;
  }

  public void setOption(int optID, Object value) throws SocketException {
    // XXX MDW Do nothing
  }

  public Object getOption(int optID) throws SocketException {
    // XXX MDW Need to implement
    return new Boolean(false);
  }

  protected int send(DatagramPacket p) throws IOException {
    return nbSendTo(p);
  }

  protected int receive(DatagramPacket p) throws IOException {
    return nbReceive(p);
  }

  protected void disconnect() throws IOException {
    nbDisconnect();
  }

  public String toString() {
    return "NonblockingSocketImpl";
  }

  protected NBIOFileDescriptor getFileDescriptor() {
    return fd;
  }

  // Multicast support
  protected void joinGroup(InetAddress address) throws IOException {
    nbJoinGroup(address);
  }
  
  protected void leaveGroup(InetAddress address) throws IOException {
    nbLeaveGroup(address);
  }
  
  protected void setTimeToLive(int ttl) throws IOException {
    nbSetTimeToLive(ttl);
  }
  
  protected int getTimeToLive() throws IOException {
    return nbGetTimeToLive();
  }
  
  protected void setInterface(InetAddress addr) throws IOException {
    // null -> INADDR_ANY
    nbSetInterface(addr);
  }

  protected void seeLocalMessages(boolean state) throws IOException {
    nbSeeLocalMessages(state);
  }

}










