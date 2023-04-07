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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * An AUdpSocket implements an asynchronous datagram socket. Applications 
 * create an AUdpSocket and associate a SinkIF with it. Packets received
 * on the socket will be pushed onto the SinkIF as AUdpInPacket objects.
 * The AUdpSocket can also be used to send messages to the socket, and to
 * associate a default send address using the connect() method.
 *
 * @author Matt Welsh
 * @see AUdpInPacket
 */
public class AUdpSocket extends SimpleSink {

  /** The default maximum packet size read by the socket. */
  public static final int DEFAULT_MAX_PACKETSIZE = 16384;

  public int maxPacketSize, writeClogThreshold;
  public SinkIF compQ;
  InetAddress localaddress, remaddress;
  int localport, remport;

  private boolean readerstarted = false;
  private boolean closed = false;

  // Internal DatagramSockState associated with this connection
  DatagramSockState sockState;

  /**
   * Create a socket bound to any available local port. This is mainly
   * used to create outgoing-only sockets.
   */
  public AUdpSocket(SinkIF compQ) throws IOException {
    this(null, 0, compQ, DEFAULT_MAX_PACKETSIZE, -1);
  }

  /**
   * Create a socket bound to the given local port. 
   */
  public AUdpSocket(int localport, SinkIF compQ) throws IOException {
    this(null, localport, compQ, DEFAULT_MAX_PACKETSIZE, -1);
  }

  /**
   * Create a socket bound to the given local address and local port.
   *
   * @param maxPacketSize The maximum size, in bytes, of packets that
   * this socket will attempt to receive. The default is 
   * DEFAULT_MAX_PACKETSIZE, which is 16 KBytes.
   *
   * @param writeClogThreshold The maximum number of outstanding writes
   * on this socket before a SinkCloggedEvent is pushed to the
   * connection's completion queue. This is effectively the maximum depth
   * threshold for this connection's SinkIF. The default value is -1, which
   * indicates that no SinkCloggedEvents will be generated.
   *
   */
  public AUdpSocket(InetAddress localaddr, int localport, SinkIF compQ, int maxPacketSize, int writeClogThreshold) throws IOException {
    this.remaddress = null;
    this.remport = -1;
    this.maxPacketSize = maxPacketSize;
    this.writeClogThreshold = writeClogThreshold;
    this.compQ = compQ;

    // Needed for getFactory() to work. Can't just call init() from
    // getFactory() as initializing aSocketMgr requires a recursive
    // call.
    aSocketMgr.init();
    this.sockState = aSocketMgr.getFactory().newDatagramSockState(this, localaddr, localport);
  }

  /**
   * Associate a SinkIF with this socket and allow data
   * to start flowing into it. When data is read, AUdpInPacket objects
   * will be pushed into the given SinkIF. If this queue is full,
   * the socket will attempt to allow packets to queue up in the O/S
   * network stack (i.e. by not issuing further read calls on the
   * socket). Until this method is called, no data will be read from 
   * the socket.
   */
  public void startReader(SinkIF receiveQ) {
    startReader(receiveQ, -1);
  }

  /**
   * Associate a SinkIF with this socket and allow data
   * to start flowing into it. When data is read, AUdpInPacket objects
   * will be pushed into the given SinkIF. If this queue is full,
   * the socket will attempt to allow packets to queue up in the O/S
   * network stack (i.e. by not issuing further read calls on the
   * socket). Until this method is called, no data will be read from 
   * the socket.
   *
   * @param readClogTries The number of times the aSocket layer will
   * attempt to push an incoming packet onto the given SinkIF while the
   * SinkIF is full. The queue entry will be dropped after this many
   * tries. The default value is -1, which indicates that the aSocket
   * layer will attempt to push the queue entry indefinitely.
   */
  public void startReader(SinkIF receiveQ, int readClogTries) {
    if (readerstarted) throw new IllegalArgumentException("startReader already called on this socket");
    aSocketMgr.enqueueRequest(new AUdpStartReadRequest(this, receiveQ, readClogTries));
    readerstarted = true;
  }

  /**
   * Enqueue an outgoing packet to be written to this socket.
   * The packet must be of type BufferElement or AUdpPacket.
   */
  public void enqueue(QueueElementIF packet) throws SinkException {
    if (closed) throw new SinkClosedException("AUdpSocket closed");
    if (packet == null) throw new BadQueueElementException("AUdpSocket.enqueue got null element", packet);
    aSocketMgr.enqueueRequest(new AUdpWriteRequest(this, (BufferElement)packet));
  }

  /**
   * Enqueue an outgoing packet to be written to this socket.
   * The packet must be of type BufferElement or AUdpPacket. Drops the packet 
   * if it cannot be enqueued.
   */
  public boolean enqueue_lossy(QueueElementIF packet) {
    if (closed) return false;
    if (packet == null) return false;
    aSocketMgr.enqueueRequest(new AUdpWriteRequest(this, (BufferElement)packet));
      return true;
  }

  /**
   * Enqueue an set of outgoing packets to this socket.
   * Each packet must be of type BufferElement or AUdpPacket.
   */
  public void enqueue_many(QueueElementIF packets[]) throws SinkException {
    if (closed) throw new SinkClosedException("AUdpSocket closed");
    for (int i = 0; i < packets.length; i++) {
      if (packets[i] == null) throw new BadQueueElementException("AUdpSocket.enqueue_many got null element", packets[i]);
      aSocketMgr.enqueueRequest(new AUdpWriteRequest(this, (BufferElement)packets[i]));
    }
  }

  /**
   * Close the socket. A SinkClosedEvent will be posted on the given
   * compQ when the close is complete.
   */
  public void close(SinkIF compQ) throws SinkClosedException {
    if (closed) throw new SinkClosedException("AUdpSocket closed");
    closed = true;
    aSocketMgr.enqueueRequest(new AUdpCloseRequest(this, compQ));
  }

  /**
   * Flush the socket. A SinkFlushedEvent will be posted on the given
   * compQ when the close is complete.
   */
  public void flush(SinkIF compQ) throws SinkClosedException {
    if (closed) throw new SinkClosedException("AUdpSocket closed");
    aSocketMgr.enqueueRequest(new AUdpFlushRequest(this, compQ));
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
   * socket. Returns 0 if this socket is not active.
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


  /**
   * Asynchronously connect this socket to the given port. All send
   * requests enqueued after this given connect call will use the
   * given address and port as the default address. An AUdpConnectEvent
   * will be pushed to the user when the connect has completed.
   */
  public void connect(InetAddress addr, int port) {
    aSocketMgr.enqueueRequest(new AUdpConnectRequest(this, addr, port));
  }

  /**
   * Asynchronously disconnect this socket from the given port.
   * An AUdpDisconnectEvent will be enqueued to the user when the
   * disconnect has completed. If this socket is not connected
   * then an AUdpDisconnectEvent will be pushed to the user regardless.
   */
  public void disconnect() {
    aSocketMgr.enqueueRequest(new AUdpDisconnectRequest(this));
  }

  /**
   * Return the InetAddress that this socket is connected to; returns
   * null if not connected.
   */
  public InetAddress getAddress() {    
    return sockState.getSocket().getInetAddress();
  }

  /**
   * Return the port that this socket is connected to; returns -1 if
   * not connected.
   */
  public int getPort() {
    return sockState.getSocket().getPort();
  }

  /**
   * Return the local InetAddress for this socket.
   */
  public InetAddress getLocalAddress() {    
    return sockState.getSocket().getLocalAddress();
  }

  /**
   * Return the local port for this socket.
   */
  public int getLocalPort() {
    return sockState.getSocket().getLocalPort();
  }

  public DatagramSocket getSocket() {
    return sockState.getSocket();
  }

}
