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


package seda.apps.Haboob.hdapi.test;

import seda.apps.Haboob.hdapi.httpRequestHandlerIF;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.lib.http.httpOKResponse;
import seda.sandStorm.lib.http.httpRequest;
import seda.sandStorm.lib.http.httpResponse;

public class Test implements httpRequestHandlerIF {

  public httpResponse handleRequest( httpRequest req ) {
    /* do something */
    System.err.println("In handleRequest of test");
    String str = "<html><body bgcolor=white><font face=helvetica><b>This is a test of the emergency broadcast system.</b><p>Your request was:"+req.toString()+"</font></body></html>";
    httpOKResponse resp = new httpOKResponse( "text/html", new BufferElement(str.getBytes()));
    return resp;
  }
}
