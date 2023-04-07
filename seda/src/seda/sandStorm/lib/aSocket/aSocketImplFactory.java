/* 
 * Copyright (c) 2002 by Matt Welsh and The Regents of the University of 
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
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * aSocketImplFactory is an internal abstract class used to represent
 * the interface between the aSocket library and a provider implementation.
 *
 * @author Matt Welsh
 */
public abstract class aSocketImplFactory {
  private static final boolean DEBUG = false;

  static aSocketImplFactory getFactory() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    aSocketImplFactory factory;

    if (aSocketMgr.USE_NIO) {
      factory = (aSocketImplFactory)Class.forName("seda.sandStorm.lib.aSocket.nio.NIOFactory").newInstance();
    } else {
      factory = (aSocketImplFactory)Class.forName("seda.sandStorm.lib.aSocket.nbio.NBIOFactory").newInstance();
    }
    return factory;
  }

  protected abstract SelectSourceIF newSelectSource();
  protected abstract SelectQueueElement newSelectQueueElement(Object item);
  protected abstract SockState newSockState(ATcpConnection conn, Socket nbsock, int writeClogThreshold) throws IOException;
  protected abstract ConnectSockState newConnectSockState(ATcpConnectRequest req, SelectSourceIF selsource) throws IOException;
  protected abstract ListenSockState newListenSockState(ATcpListenRequest req, SelectSourceIF selsource) throws IOException;
  protected abstract DatagramSockState newDatagramSockState(AUdpSocket sock, InetAddress addr, int port) throws IOException;

}
