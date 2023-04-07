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


/*
 * This is a simple test program demonstrating the NBIO library.
 * It creates a nonblocking socket and writes packets to it.
 */

import seda.nbio.NonblockingOutputStream;
import seda.nbio.NonblockingSocket;

public class NBIOClient {

  public static void main(String args[]) {
    if (args.length != 1) {
      System.err.println("Usage: NBIOClient <hostname>\n");
      System.exit(-1);
    }

    try {
      System.err.println("NBIOClient starting...");
      NonblockingSocket s = new NonblockingSocket(args[0], 4046);

      NonblockingOutputStream os = (NonblockingOutputStream)s.getOutputStream();
      String str = "Hello there server!";
      byte barr[] = str.getBytes();

      while (true) {
        int c = os.nbWrite(barr);
	System.err.println("WROTE "+c+" bytes");
	try {
	  Thread.sleep(1000);
	} catch (InterruptedException ie) {
        }
      }

    } catch (Exception e) {
      System.err.println("NBIOClient: Caught exception: "+e);
      e.printStackTrace();
      System.exit(-1);
    }

  }

}
