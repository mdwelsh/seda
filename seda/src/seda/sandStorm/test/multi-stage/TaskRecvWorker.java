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


import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.lib.aSocket.ATcpConnection;
import seda.sandStorm.lib.aSocket.ATcpInPacket;
import seda.sandStorm.lib.aSocket.ATcpServerSocket;

import java.io.IOException;

/**
 * This event handmler listens for connections on the TCP port PORT
 * and accepts packets of size TASK_SIZE. For each packet it delivers
 * a BufferElement to the next handler on the chain, specified by 
 * the "next_handler" argument. 
 */
public class TaskRecvWorker implements EventHandlerIF {

  private static final boolean DEBUG = false;

  // Spin in a loop trying to push packets to the next worker
  private static final boolean DISPATCH_SPIN = true;

  private SinkIF      mySink;
  private SinkIF      nextWorker, completionWorker;

  private ATcpConnection incomingConnection;

  private ATcpServerSocket servsock;
  private int PORT = 5002;
  private int TASK_SIZE = 200;
  private byte cur_task[];
  private int cur_offset;

  public TaskRecvWorker() {
  }

  public void init(ConfigDataIF config) throws Exception {
    this.mySink = config.getStage().getSink();

    if (config.getString("port") != null) 
      PORT = Integer.parseInt(config.getString("port"));
    if (config.getString("task_size") != null) 
      TASK_SIZE = Integer.parseInt(config.getString("task_size"));

    cur_task = new byte[TASK_SIZE];
    cur_offset = 0;

    if (config.getString("next_handler") == null) {
      System.err.println("Must specify next_handler!");
      System.exit(-1);
    }

    nextWorker = config.getManager().getStage(config.getString("next_handler")).getSink();

    if (config.getString("completion_handler") == null) {
      System.err.println("Must specify completion_handler!");
      System.exit(-1);
    }

    completionWorker = config.getManager().getStage(config.getString("completion_handler")).getSink();

    try {
      servsock = new ATcpServerSocket(PORT, mySink);
    } catch (IOException e) {
      System.err.println("Got exception creating server socket: "+e);
      e.printStackTrace();
      System.exit(-1);
    }

    System.err.println("task_size="+TASK_SIZE);
  }

  public void destroy() {
  }

  public synchronized void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("GOT QEL: "+item);

    if (item instanceof ATcpConnection) {
      System.err.println("Got connection: "+item);
      incomingConnection = (ATcpConnection)item;
      incomingConnection.startReader(mySink);
      completionWorker.enqueue_lossy(incomingConnection);

    } else if (item instanceof ATcpInPacket) {
      ATcpInPacket pkt = (ATcpInPacket)item;
      byte in[] = pkt.getBytes();
      int inlength = in.length;
      int inoff = 0;
      if (DEBUG) System.err.println("Received "+inlength+" bytes");

      do {
        int tocopy = Math.min(inlength-inoff, TASK_SIZE-cur_offset);
        System.arraycopy(in, inoff, cur_task, cur_offset, tocopy);
        cur_offset += tocopy;
	inoff += tocopy;
        if (cur_offset == TASK_SIZE) {
          BufferElement task = new BufferElement(cur_task);
	  if (DEBUG) System.err.println("Sending task of size "+cur_task.length);
	  if (DISPATCH_SPIN) {
	    boolean sent = false;
	    while (!sent) {
    	      try {
                nextWorker.enqueue(task);
	        sent = true;
              } catch (SinkFullException sfe) {
	        System.err.println("Got SFE: "+sfe);
	      } catch (SinkException sce) {
	        System.err.println("Got SE: "+sce);
		try {
		  incomingConnection.close(null);
		} catch (SinkClosedException sce2) {
		  // Ignore
		}
	      }
	    }
	  } else {
    	    try {
              nextWorker.enqueue(task);
            } catch (SinkFullException sfe) {
	      System.err.println("Got SFE: "+sfe);
	    } catch (SinkException sce) {
	      System.err.println("Got SE: "+sce);
      	      try {
		incomingConnection.close(null);
	      } catch (SinkClosedException sce2) {
		// Ignore
	      }
	    }
          }
	  cur_task = new byte[TASK_SIZE];
	  cur_offset = 0;
        }
      } while ((inlength - inoff) > 0);

    } else {
      System.err.println("Got unexpected event: "+item);
    }

  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

}

