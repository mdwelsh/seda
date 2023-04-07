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

package seda.sandStorm.lib.aSocket.nbio;

import seda.nbio.SelectItem;
import seda.sandStorm.lib.aSocket.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * The NBIO implementation of aSocketImplFactory.
 * 
 * @author Matt Welsh
 */
public class NBIOFactory extends aSocketImplFactory {
  private static final boolean DEBUG = false;

  protected SelectSourceIF newSelectSource() {
    return new SelectSource();
  }

  protected seda.sandStorm.lib.aSocket.SelectQueueElement newSelectQueueElement(Object item) {
    return new seda.sandStorm.lib.aSocket.nbio.SelectQueueElement((SelectItem)item);
  }

  protected seda.sandStorm.lib.aSocket.SockState newSockState(ATcpConnection conn, Socket nbsock, int writeClogThreshold) throws IOException {
    return new seda.sandStorm.lib.aSocket.nbio.SockState(conn, nbsock, writeClogThreshold);
  }

  protected seda.sandStorm.lib.aSocket.ConnectSockState newConnectSockState(ATcpConnectRequest req, SelectSourceIF selsource) throws IOException {
    return new seda.sandStorm.lib.aSocket.nbio.ConnectSockState(req, selsource);
  }

  protected seda.sandStorm.lib.aSocket.ListenSockState newListenSockState(ATcpListenRequest req, SelectSourceIF selsource) throws IOException {
    return new seda.sandStorm.lib.aSocket.nbio.ListenSockState(req, selsource);
  }

  protected seda.sandStorm.lib.aSocket.DatagramSockState newDatagramSockState(AUdpSocket sock, InetAddress addr, int port) throws IOException {
    return new seda.sandStorm.lib.aSocket.nbio.DatagramSockState(sock, addr, port);
  }

}
