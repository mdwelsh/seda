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

import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.SimpleSink;

import java.net.InetAddress;
import java.net.Socket;

/**
 * An ATcpConnection represents an established connection on an asynchronous
 * socket. It is used to send outgoing packets over the connection, and
 * to initiate packet reads from the connection. When a packet arrives on
 * this connection, an ATcpInPacket object will be pushed to the SinkIF
 * specified by the startReader() call. The ATcpInPacket will contain a
 * pointer to this ATcpConnection. This object also allows the connection
 * to be flushed or closed.
 *
 * @author Matt Welsh
 * @see ATcpInPacket
 *
 */
public class ATcpConnection extends SimpleSink implements QueueElementIF {
  
  private InetAddress address;
  private int port;
  private boolean closed;
  private boolean readerstarted;

  private ATcpClientSocket clientSocket;
  private ATcpServerSocket serverSocket;

  // Internal SockState associated with this connection
  public SockState sockState;

  /** 
   * The application may use this field to associate some 
   * application-specific state with this connection. The aSocket
   * layer will not read or modify this field in any way.
   */
  public Object userTag;

  private ATcpConnection(InetAddress address, int port) {
    this.address = address;
    this.port = port;
    this.closed = false;
  }

  public ATcpConnection(ATcpClientSocket cliSock, InetAddress address, int port) {
    this(address, port);
    this.clientSocket = cliSock;
  }

  public ATcpConnection(ATcpServerSocket servSock, InetAddress address, int port) {
    this(address, port);
    this.serverSocket = servSock;
  }

  protected ATcpConnection() {
  }

  /** 
   * Return the address of the peer.
   */
  public InetAddress getAddress() {
    return address;
  }

  /**
   * Return the port of the peer.
   */
  public int getPort() {
    return port;
  }

  public boolean isClosed() { return closed || (closed = sockState.isClosed()); }

  /**
   * Return the ATcpServerSocket from which this connection came.
   * Returns null if this connection resulted from an ATcpClientSocket.
   */
  public ATcpServerSocket getServerSocket() {
    return serverSocket;
  }

  /**
   * Return the ATcpClientSocket from which this connection came.   
   * Returns null if this connection resulted from an ATcpServerSocket.
   */
  public ATcpClientSocket getClientSocket() {
    return clientSocket;
  }

  /**
   * Returns the low-level Socket object associated with this connection.
   */
  public Socket getSocket() {
    return sockState.nbsock;
  }

  /** 
   * Associate a SinkIF with this connection and allow data
   * to start flowing into it. When data is read, ATcpInPacket objects
   * will be pushed into the given SinkIF. If this sink is full,
   * the connection will attempt to allow packets to queue up in the O/S
   * network stack (i.e. by not issuing further read calls on the
   * socket). Until this method is called, no data will be read from 
   * the socket.
   */
  public void startReader(SinkIF receiveQ) {
    if (readerstarted) throw new IllegalArgumentException("startReader already called on this connection");
    aSocketMgr.enqueueRequest(new ATcpStartReadRequest(this, receiveQ, -1));
    readerstarted = true;
  }

  /** 
   * Associate a SinkIF with this connection and allow data
   * to start flowing into it. When data is read, ATcpInPacket objects
   * will be pushed into the given SinkIF. If this queue is full,
   * the connection will attempt to allow packets to queue up in the O/S
   * network stack (i.e. by not issuing further read calls on the
   * socket). Until this method is called, no data will be read from 
   * the socket.
   *
   * @param readClogTries The number of times the aSocket layer will
   * attempt to push a new entry onto the given SinkIF while the
   * SinkIF is full. The queue entry will be dropped after this many
   * tries. The default value is -1, which indicates that the aSocket
   * layer will attempt to push the queue entry indefinitely.
   */
  public void startReader(SinkIF receiveQ, int readClogTries) {
    if (readerstarted) throw new IllegalArgumentException("startReader already called on this connection");
    aSocketMgr.enqueueRequest(new ATcpStartReadRequest(this, receiveQ, readClogTries));
    readerstarted = true;
  }

  /**
   * Enqueue an outgoing packet to be written to this socket.
   */
  public void enqueue(QueueElementIF buf) throws SinkException {
    if (isClosed()) throw new SinkClosedException("ATcpConnection closed");
    if (buf == null) throw new BadQueueElementException("ATcpConnection.enqueue got null element", buf);
    aSocketMgr.enqueueRequest(new ATcpWriteRequest(this, (BufferElement)buf));
  }

  /**
   * Enqueue an outgoing packet to be written to this socket. 
   * Drops the packet if it cannot be enqueued.
   */
  public boolean enqueue_lossy(QueueElementIF buf) {
    if (isClosed()) return false;
    if (buf == null) return false;
    aSocketMgr.enqueueRequest(new ATcpWriteRequest(this, (BufferElement)buf));
    return true;
  }

  /**
   * Enqueue a set of outgoing packets to be written to this socket.
   */
  public void enqueue_many(QueueElementIF bufarr[]) throws SinkException {
    if (isClosed()) throw new SinkClosedException("ATcpConnection closed");
    for (int i = 0; i < bufarr.length; i++) {
      if (bufarr[i] == null) throw new BadQueueElementException("ATcpConnection.enqueue_many got null element", bufarr[i]);
      aSocketMgr.enqueueRequest(new ATcpWriteRequest(this, (BufferElement)bufarr[i]));
    }
  }

  /**
   * Close the socket. A SinkClosedEvent will be posted on the given
   * compQ when the close is complete.
   */
  public void close(SinkIF compQ) throws SinkClosedException { 
    if (isClosed()) throw new SinkClosedException("ATcpConnection closed");
    closed = true;
    aSocketMgr.enqueueRequest(new ATcpCloseRequest(this, compQ));
  }

  /**
   * Flush the socket. A SinkFlushedEvent will be posted on the given
   * compQ when the close is complete.
   */
  public void flush(SinkIF compQ) throws SinkClosedException { 
    if (isClosed()) throw new SinkClosedException("ATcpConnection closed");
    aSocketMgr.enqueueRequest(new ATcpFlushRequest(this, compQ));
  } 

  /**
   * Returns the number of elements currently waiting in the sink.
   */
  public int size() {
    if (sockState == null) return 0;
    if (sockState.writeReqList == null) return 0; // If closed
    else return sockState.writeReqList.size();
  }

  /**
   * Returns the next sequence number for packets arriving on this 
   * connection. Returns 0 if this connection is not active.
   * Note that this method may return an <b>inaccurate</b> sequence
   * number since the call is not synchronized with new message
   * arrivals that may increment the sequence number.
   */
  public long getSequenceNumber() {
    if (sockState == null) return 0;
    return sockState.seqNum;
  }

  /**
   * Returns the profile size of this connection.
   */
  public int profileSize() {
    return size();
  }

  public String toString() {
    return "ATcpConnection ["+address.getHostAddress()+":"+port+"/"+sockState+"/"+clientSocket+"]";
  }


}
