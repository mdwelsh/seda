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
import seda.util.Tracer;

import java.io.IOException;

/**
 * Internal event handler used to handle socket write events.
 */
class WriteEventHandler extends aSocketEventHandler implements EventHandlerIF, aSocketConst {

  private static final boolean DEBUG = false;
  private static final boolean PROFILE = false;

  static Tracer tracer;

  WriteEventHandler() {
    if (PROFILE) tracer = new Tracer("aSocket WriteEH");
  }

  public void init(ConfigDataIF config) {
  }

  public void destroy() {
  }

  private void processConnection(ConnectSockState css) throws IOException {
    if (DEBUG) System.err.println("WriteThread: processConnection called for "+css);
    css.complete();
  }          

  private void processTcpWrite(SockState ss) throws IOException {
    if (DEBUG) System.err.println("WriteEventHandler: processTcpWrite called");

    // Socket already closed; just forget about it
    if (ss.closed) return;

    // Process queue of requests
    if (DEBUG) System.err.println("WriteEventHandler: "+ss+" has "+ss.outstanding_writes+" pending requests");
    if (ss.outstanding_writes == 0) {
      ss.numEmptyWrites++;
      if ((WRITE_MASK_DISABLE_THRESHOLD != -1) && 
	  (ss.numEmptyWrites >= WRITE_MASK_DISABLE_THRESHOLD)) {
	ss.writeMaskDisable();
      }
      if (DEBUG) System.err.println("WriteEventHandler: Socket has no pending writes, numEmptyWrites="+ss.numEmptyWrites);
      return;
    }

    aSocketRequest req;

    // Avoid doing too many things on each socket
    int num_reqs_processed = 0;
    while (ss.writeReqList != null  &&   
           ((req = (aSocketRequest)ss.writeReqList.get_head()) != null) &&
	(++num_reqs_processed < MAX_WRITE_REQS_PER_SOCKET)) {

      if (DEBUG) System.err.println("Processing "+req+" ("+num_reqs_processed+")");

      if (req instanceof ATcpWriteRequest) {
	// Handle write request
	if (DEBUG) System.err.println("WriteEventHandler: Processing ATcpWriteRequest");
	ATcpWriteRequest wreq = (ATcpWriteRequest)req;

	// Skip if locked
	if ((ss.cur_write_req != null) && (ss.cur_write_req != req)) break;

	if (ss.cur_write_req == null) {
	  if (DEBUG) System.err.println("WriteEventHandler: Doing initWrite");
          if (PROFILE) tracer.trace("initWrite called");
	  ss.initWrite((ATcpWriteRequest)req);
	}

	boolean done = false;
	int c = 0;

	// Try hard to finish this packet
	try {
	  while ((!(done = ss.tryWrite())) && (c++ < TRYWRITE_SPIN)) ;
	} catch (SinkClosedException sde) {
	  // OK, the socket closed underneath us
	  // XXX MDW: Taking this out for now - expect the SinkClosedEvent
	  // to be pushed up when read() fails

	  //SinkIF cq = wreq.buf.getCompletionQueue();
	  //if (cq != null) {
	  //  SinkClosedEvent sce = new SinkClosedEvent(wreq.conn);
	  //  cq.enqueue_lossy(sce);
	  //}
	}

	if (done) {
	  if (DEBUG) System.err.println("WriteEventHandler: Finished write");
	  // Finished this write
          if (PROFILE) tracer.trace("writeReset");
	  ss.writeReset();
          if (PROFILE) tracer.trace("writeReset return");

	  // Send completion upcall
	  SinkIF cq = wreq.buf.getCompletionQueue();
	  if (cq != null) {
	    SinkDrainedEvent sde = new SinkDrainedEvent(ss.conn, wreq.buf);
	    cq.enqueue_lossy(sde);
	  }

	  // Clear the request
	  if (!ss.isClosed()) {
	    ss.writeReqList.remove_head();
	  } else {
	    return; // Nothing more to do
	  }

	} else {
	  if (DEBUG) System.err.println("WriteEventHandler: Write not completed");
	  break; // Don't want to process anything else here
	}

      } else if (req instanceof ATcpFlushRequest) {

	ATcpFlushRequest freq = (ATcpFlushRequest)req;

	// Skip if locked
	if ((ss.cur_write_req != null) && (ss.cur_write_req != req)) break;

	// OK - by the time we have the lock we can claim the flush is done
        if(freq.compQ != null) {
           // JRVB: added check to avoid NullPointerException
	SinkFlushedEvent sfe = new SinkFlushedEvent(freq.conn);
	freq.compQ.enqueue_lossy(sfe);
        }

	// Clear the request
	if (!ss.isClosed()) {
	  ss.writeReqList.remove_head();
	  ss.writeReset();
	} else {
	  return; // Nothing more to do
	}

      } else if (req instanceof ATcpCloseRequest) {

	ATcpCloseRequest creq = (ATcpCloseRequest)req;

	// Skip if locked
	if ((ss.cur_write_req != null) && (ss.cur_write_req != req)) break;

	// OK - by the time we have the lock we can claim the close is done
       	ss.close(creq.compQ);

	return; 

      } else {
	throw new IllegalArgumentException("Invalid incoming request to WriteEventHandler: "+req);
      }
    }

    if (DEBUG) System.err.println("WriteEventHandler: Processed "+num_reqs_processed+" writes in one go");

  }

  private void processUdpWrite(DatagramSockState ss) throws IOException {

    if (DEBUG) System.err.println("WriteEventHandler: processUdpWrite called");

    // Socket already closed; just forget about it
    if (ss.closed) return;

    // Process queue of requests
    if (DEBUG) System.err.println("WriteEventHandler: "+ss+" has "+ss.outstanding_writes+" pending requests");
    if (ss.outstanding_writes == 0) {
      ss.numEmptyWrites++;
      if ((WRITE_MASK_DISABLE_THRESHOLD != -1) && 
	  (ss.numEmptyWrites >= WRITE_MASK_DISABLE_THRESHOLD)) {
	ss.writeMaskDisable();
      }
      if (DEBUG) System.err.println("WriteEventHandler: Socket has no pending writes, numEmptyWrites="+ss.numEmptyWrites);
      return;
    }

    aSocketRequest req;

    // Avoid doing too many things on each socket
    int num_reqs_processed = 0;
    while (((req = (aSocketRequest)ss.writeReqList.get_head()) != null) &&
	(++num_reqs_processed < MAX_WRITE_REQS_PER_SOCKET)) {

      if (DEBUG) System.err.println("Processing "+req+" ("+num_reqs_processed+")");

      if (req instanceof AUdpWriteRequest) {
	// Handle write request
	if (DEBUG) System.err.println("WriteEventHandler: Processing AUdpWriteRequest");
	AUdpWriteRequest wreq = (AUdpWriteRequest)req;

	// Skip if locked
	if ((ss.cur_write_req != null) && (ss.cur_write_req != req)) break;

	if (ss.cur_write_req == null) {
	  if (DEBUG) System.err.println("WriteEventHandler: Doing initWrite");
	  ss.initWrite(wreq);
	}

	boolean done = false;
	int c = 0;

	// Try hard to finish this packet
	try {
	  while ((!(done = ss.tryWrite())) && (c++ < TRYWRITE_SPIN)) ;
	} catch (SinkClosedException sde) {
	  // Ignore - expect the SinkClosedEvent to be pushed up when 
	  // receive() fails
	}

	if (done) {
	  if (DEBUG) System.err.println("WriteEventHandler: Finished write");
	  // Finished this write
	  ss.writeReset();

	  // Send completion upcall
	  SinkIF cq = wreq.buf.getCompletionQueue();
	  if (cq != null) {
	    SinkDrainedEvent sde = new SinkDrainedEvent(ss.udpsock, wreq.buf);
	    cq.enqueue_lossy(sde);
	  }

	  // Clear the request
	  if (!ss.isClosed()) {
	    ss.writeReqList.remove_head();
	  } else {
	    return; // Nothing more to do
	  }

	} else {
	  if (DEBUG) System.err.println("WriteEventHandler: Write not completed");
	  break; // Don't want to process anything else here
	}

      } else if (req instanceof AUdpFlushRequest) {

	AUdpFlushRequest freq = (AUdpFlushRequest)req;

	// Skip if locked
	if ((ss.cur_write_req != null) && (ss.cur_write_req != req)) break;

	// OK - by the time we have the lock we can claim the flush is done
	SinkFlushedEvent sfe = new SinkFlushedEvent(freq.sock);
	freq.compQ.enqueue_lossy(sfe);

	// Clear the request
	if (!ss.isClosed()) {
	  ss.writeReqList.remove_head();
	} else {
	  return; // Nothing more to do
	}

      } else if (req instanceof AUdpCloseRequest) {

	AUdpCloseRequest creq = (AUdpCloseRequest)req;

	// Skip if locked
	if ((ss.cur_write_req != null) && (ss.cur_write_req != req)) break;

	// OK - by the time we have the lock we can claim the close is done
       	ss.close(creq.compQ);

	return; 

      } else {
	throw new IllegalArgumentException("Invalid incoming request to WriteEventHandler: "+req);
      }
    }

    if (DEBUG) System.err.println("WriteEventHandler: Processed "+num_reqs_processed+" writes in one go");
  }

  private void processWriteRequest(aSocketRequest req) throws IOException {

    if (req instanceof ATcpConnectRequest) {

      // This registers itself
      ConnectSockState ss;
      ss = aSocketMgr.getFactory().newConnectSockState((ATcpConnectRequest)req, selsource);

    } else if (req instanceof AUdpConnectRequest) {

      if (DEBUG) System.err.println("WriteEventHandler: processing AUdpConnectRequest: " + req);
      AUdpConnectRequest creq = (AUdpConnectRequest)req;
      AUdpSocket udpsock = creq.sock;
      udpsock.sockState.connect(creq.addr, creq.port);
      // only works in jdk1.4
//      if (DEBUG) System.err.println("connected = " + udpsock.getSocket().isConnected());
      AUdpConnectEvent ev = new AUdpConnectEvent(udpsock);
      udpsock.compQ.enqueue_lossy(ev);

    } else if (req instanceof AUdpDisconnectRequest) {

      AUdpDisconnectRequest dreq = (AUdpDisconnectRequest)req;
      AUdpSocket udpsock = dreq.sock;
      udpsock.getSocket().disconnect();
      AUdpDisconnectEvent ev = new AUdpDisconnectEvent(udpsock);
      udpsock.compQ.enqueue_lossy(ev);

    } else if (req instanceof ATcpWriteRequest) {

      if (DEBUG) System.err.println("WriteEventHandler: got write request: " + req);
      if (PROFILE) tracer.trace("processWriteRequest (TCP)");

      SockState ss = ((ATcpWriteRequest)req).conn.sockState;

      // If already closed, just drop it
      if (!ss.closed) {
	if (DEBUG) System.err.println("WriteEventHandler: Adding write req to "+ss);

	if (!ss.addWriteRequest(req, selsource)) {
	  // Couldn't enqueue: this connection is clogged
	  ATcpWriteRequest wreq = (ATcpWriteRequest)req;
	  SinkIF cq = wreq.buf.getCompletionQueue();
	  if (cq != null) {
	    SinkCloggedEvent sce = new SinkCloggedEvent(wreq.conn, wreq.buf);
	    cq.enqueue_lossy(sce);
	  }
	} else {
            if (DEBUG) System.err.println("WriteEventHandler: " + ss.outstanding_writes + " outstanding writes" );
            if (PROFILE) tracer.trace("done enqueue writereq");
        }
      }

    } else if (req instanceof AUdpWriteRequest) {

      DatagramSockState ss = ((AUdpWriteRequest)req).sock.sockState;

      // If already closed, just drop it
      if (!ss.closed) {
	if (DEBUG) System.err.println("WriteEventHandler: Adding write req to "+ss);

	if (!ss.addWriteRequest(req, selsource)) {
	  // Couldn't enqueue: this connection is clogged
	  AUdpWriteRequest wreq = (AUdpWriteRequest)req;
	  SinkIF cq = wreq.buf.getCompletionQueue();
	  if (cq != null) {
	    SinkCloggedEvent sce = new SinkCloggedEvent(wreq.sock, wreq.buf);
	    cq.enqueue_lossy(sce);
	  }
	}
      }

    } else if (req instanceof ATcpCloseRequest) {

      SockState ss = ((ATcpCloseRequest)req).conn.sockState;

      // If there is no pending outgoing data, do immediate close
      if (ss.outstanding_writes == 0) {
       	ss.close(((ATcpCloseRequest)req).compQ);
      } else {
	// Queue it up
	ss.addWriteRequest(req, selsource);
      }

    } else if (req instanceof ATcpFlushRequest) {

      SockState ss = ((ATcpFlushRequest)req).conn.sockState;
      ss.addWriteRequest(req, selsource);

    } else if (req instanceof AUdpCloseRequest) {

      DatagramSockState ss = ((AUdpCloseRequest)req).sock.sockState;
      ss.addWriteRequest(req, selsource);

    } else if (req instanceof AUdpFlushRequest) {

      DatagramSockState ss = ((AUdpFlushRequest)req).sock.sockState;
      ss.addWriteRequest(req, selsource);

    } else {
      throw new IllegalArgumentException("Bad request type to enqueueWrite");
    }
  }


  public void handleEvent(QueueElementIF qel) {
    if (DEBUG) System.err.println("WriteEventHandler: Got QEL: "+qel);

    try {

        if (qel instanceof SelectQueueElement) {
            Object obj;
            obj = ((SelectQueueElement)qel).getAttachment();

            if (obj instanceof ConnectSockState) {
                processConnection((ConnectSockState)obj);
            } else {
                if (qel instanceof SelectQueueElement)
                    ((SelectQueueElement)qel).clearEvents();
                if (obj instanceof SockState) {
                   processTcpWrite((SockState)obj);
                } else {
                    processUdpWrite((DatagramSockState)obj);
                }
            }

      } else if (qel instanceof aSocketRequest) {
	processWriteRequest((aSocketRequest)qel);

      } else {
	throw new IllegalArgumentException("WriteEventHandler: Got unknown event type "+qel);
      }

    } catch (Exception e) {
      System.err.println("WriteEventHandler: Got exception: "+e);
      e.printStackTrace();
    }
  }

  public void handleEvents(QueueElementIF qelarr[]) {
    int numWrites = 0;

    for (int i = 0; i < qelarr.length; i++) {

      try {

	QueueElementIF qel = qelarr[i];

    if (DEBUG) System.err.println("WriteEventHandler: Got QEL: " + qel);
	if (qel instanceof SelectQueueElement) {
            Object obj;
            obj = ((SelectQueueElement)qel).getAttachment();
            if (DEBUG) System.err.println("!!!obj= " + obj);

            if (obj instanceof ConnectSockState) {
                processConnection((ConnectSockState)obj);
            } else {

                if ((MAX_WRITES_AT_ONCE == -1) ||
                    (numWrites++ < MAX_WRITES_AT_ONCE)) {
                    if (qel instanceof SelectQueueElement)
                        ((SelectQueueElement)qel).clearEvents();
                    if (obj instanceof SockState) {
                        processTcpWrite((SockState)obj);
                    } else {
                        processUdpWrite((DatagramSockState)obj);
                    }
	        }
            }
	} else if (qel instanceof aSocketRequest) {
	  processWriteRequest((aSocketRequest)qel);

	} else {
	  throw new IllegalArgumentException("ReadEventHandler: Got unknown event type "+qel);
	}

      } catch (Exception e) {
	System.err.println("WriteEventHandler: Got exception: "+e);
	e.printStackTrace();
      }
    }
  }

}

