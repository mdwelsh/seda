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
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.lib.http.*;

/**
 * Simple test program demonstrating use of the Sandstorm http library.
 * Creates a simple HTTP server which coughs up a static web
 * page for each request.
 *
 * @author Matt Welsh
 */
public class TestServer implements EventHandlerIF {

  private static final boolean DEBUG = false;
  private static final int PORT = 8080;

  private SinkIF mysink;

  public void init(ConfigDataIF config) throws Exception {
    mysink = config.getStage().getSink();

    System.err.println("TestServer: Started");

    httpServer server = new httpServer(config.getManager(), mysink, PORT);
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("GOT QEL: "+item);

    if (item instanceof httpConnection) {
      System.err.println("TestServer: Got connection "+item);

    } else if (item instanceof httpRequest) {
      handleRequest((httpRequest)item);

    } else {
      if (DEBUG) System.err.println("Got unknown event type: "+item);
    }

  }

  public void handleEvents(QueueElementIF items[]) {
    if (DEBUG) System.err.println("GOT "+items.length+" ELEMENTS");
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

  private void handleRequest(httpRequest req) {
    String url = req.getURL();
    System.err.println("TestServer: Got request "+url);

    String response = "<html><body bgcolor=\"white\"><h3>Sandstorm Web Server Response</h3><p><b>Hello, this is the Sandstorm test web server.</b><br>You requested the following URL: <p><tt>"+url+"</tt><p>Your complete request was as follows: <p><pre>"+req.toString()+"</pre><p>Glad to be of service today.</body></html>";
    BufferElement resp = new BufferElement(response.getBytes());
    httpOKResponse ok = new httpOKResponse("text/html", resp);
    req.getConnection().enqueue_lossy(new httpResponder(ok, req));
  }

}

