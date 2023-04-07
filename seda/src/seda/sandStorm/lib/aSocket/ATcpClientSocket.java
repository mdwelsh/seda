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

package seda.sandStorm.lib.aSocket;

import seda.sandStorm.api.SinkIF;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * An ATcpClientSocket implements an asynchronous outgoing socket connection.
 * Applications create an ATcpClientSocket and associate a SinkIF with it.
 * When the connection is established an ATcpConnection object will be pushed 
 * to the given SinkIF. The ATcpConnection is then used for actual
 * communication.
 *
 * <p> A Sandstorm stage would use this as follows:
 * <pre>
 *    SinkIF mySink = config.getSink();  // 'config' passed to stage init()
 *    ATcpClientSocket sock = new ATcpClientSocket(addr, port, mySink); 
 * </pre>
 *
 * @author Matt Welsh
 * @see ATcpConnection
 */
public class ATcpClientSocket {

  private InetAddress address;
  private int port;

  /**
   * Create a socket connecting to the given address and port.
   * An ATcpConnection will be posted to the given SinkIF when the
   * connection is established. If an error occurs, an
   * ATcpConnectFailedEvent will be posted instead.
   */
  public ATcpClientSocket(InetAddress addr, int port, SinkIF compQ) {
    this(addr, port, compQ, -1, -1);
  }

  /**
   * Create a socket connecting to the given host and port.
   * An ATcpConnection will be posted to the given compQ when the
   * connection is established. If an error occurs, an
   * ATcpConnectFailedEvent will be posted instead.
   */
  public ATcpClientSocket(String host, int port, SinkIF compQ) 
    throws UnknownHostException {
    this(InetAddress.getByName(host), port, compQ, -1, -1);
  }

  /**
   * Create a socket connecting to the given address and port with the
   * given writeClogThreshold value.  
   * An ATcpConnection will be posted to the given compQ when the
   * connection is established. If an error occurs, an
   * ATcpConnectFailedEvent will be posted instead.
   *
   * @param writeClogThreshold The maximum number of outstanding writes
   * on this socket before a SinkCloggedEvent is pushed to the
   * connection's completion queue. This is effectively the maximum depth
   * threshold for this connection's SinkIF. The default value is -1, which
   * indicates that no SinkCloggedEvents will be generated.
   *
   * @param connectClogTries The number of times the aSocket layer will
   * attempt to push a new entry onto the given SinkIF while the
   * SinkIF is full. The queue entry will be dropped after this many
   * tries. The default value is -1, which indicates that the aSocket
   * layer will attempt to push the queue entry indefinitely.
   *
   */
  public ATcpClientSocket(InetAddress addr, int port, SinkIF compQ, int writeClogThreshold, int connectClogTries) {
    this.address = addr;
    this.port = port;
    aSocketMgr.enqueueRequest(new ATcpConnectRequest(this, addr, port, compQ, writeClogThreshold, connectClogTries));
  }

  /**
   * Create a socket connecting to the given host and port with the
   * given writeClogThreshold value.
   * An ATcpConnection will be posted to the given compQ when the
   * connection is established. If an error occurs, an
   * ATcpConnectFailedEvent will be posted instead.
   *
   * @param writeClogThreshold The maximum number of outstanding writes
   * on this socket before a SinkCloggedEvent is pushed to the
   * connection's completion queue. This is effectively the maximum depth
   * threshold for this connection's SinkIF. The default value is -1, which
   * indicates that no SinkCloggedEvents will be generated.
   *
   * @param connectClogTries The number of times the aSocket layer will
   * attempt to push a new entry onto the given SinkIF while the
   * SinkIF is full. The queue entry will be dropped after this many
   * tries. The default value is -1, which indicates that the aSocket
   * layer will attempt to push the queue entry indefinitely.
   *
   */
  public ATcpClientSocket(String host, int port, SinkIF compQ, 
      int writeClogThreshold, int connectClogTries) 
    throws UnknownHostException {
    this(InetAddress.getByName(host), port, compQ, writeClogThreshold, connectClogTries);
  }

  protected ATcpClientSocket() {
  }

  /**
   * Return the InetAddress which this socket is connected to.
   */
  public InetAddress getAddress() {    
    return address;  
  }

  /**
   * Return the port which this socket is connected to.
   */
  public int getPort() {
    return port;  
  }

//  public String toString() {
//    return "ATcpClientSocket ["+address+":"+port+"]";
//  }
}
