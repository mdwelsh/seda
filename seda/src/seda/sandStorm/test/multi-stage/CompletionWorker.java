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


import seda.sandStorm.api.ConfigDataIF;
import seda.sandStorm.api.EventHandlerIF;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkException;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.lib.aSocket.ATcpConnection;

/**
 * This handler accepts BufferElement and sends an ack of ACK_SIZE
 * to the TCP connection previously established by a TaskRecvWorker.
 *
 */
public class CompletionWorker implements EventHandlerIF {

  private static final boolean DEBUG = false;

  private ATcpConnection outgoingConnection;
  private static final int ACK_SIZE = 32;
  private int num_received = 0;
  private byte ack[], ack_tagged[];
  private BufferElement buf, buf_tagged;

  public CompletionWorker() {
  }

  public void init(ConfigDataIF config) {
    ack = new byte[ACK_SIZE];
    ack[0] = (byte)0;
    buf = new BufferElement(ack);
    ack_tagged = new byte[ACK_SIZE];
    ack_tagged[0] = (byte)1;
    buf_tagged = new BufferElement(ack_tagged);
    System.err.println("Started");
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("GOT QEL: "+item);

    if (item instanceof ATcpConnection) {
      if (outgoingConnection != null) {
	System.err.println("CompletionWorker: Warning: outgoingConnection already received!");
      } else {
	outgoingConnection = (ATcpConnection)item;
      }
    
    } else if (item instanceof BufferElement) {
      BufferElement task = (BufferElement)item;

      num_received++;

      // Send ack
      if (DEBUG) System.err.println("Sending ack!");

      BufferElement tosend;
      if (task.getBytes()[0] != 0) {
	// Tagged message
	tosend = buf_tagged;
      } else {
	tosend = buf;
      }

      try {
        outgoingConnection.enqueue(buf);
      } catch (SinkException se) {
        System.err.println("Got SinkException: "+se);
      }

    } else {
      System.err.println("Got unexpected event: "+item);
    }

  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

}

