/*
 * Copyright (c) 2002 by The Regents of the University of California. 
 * All rights reserved.
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
 * Author: Dennis Chi <denchi@uclink4.berkeley.edu> 
 *
 */

package seda.sandStorm.lib.aTLS;

import java.util.Comparator;
import java.util.TreeSet;

/**
 * aTLSOrderData is used to order data for the Encrypt Stage and Handshake Stage
 * because the ordering of packets is important, and this class is necessary if
 * attempting to multithread those stages.
 */
public class aTLSOrderData {
    private TreeSet outoforder;
    private long nextSeqNum;

    public aTLSOrderData() {
	outoforder = new TreeSet (new Orderer());
    }

    class Orderer implements Comparator {
	public int compare(Object o1, Object o2) {
	    // decide what to return based on what to compare
	    // for now, the only thing to compare will be handshake messages
	    return 0;
	}
    }
    
    public void push(Object obj) {
	if (obj instanceof aTLSCipherSpecPacket) {

	}
	else if (obj instanceof aTLSHandshakePacket) {

	}
	outoforder.add(obj);
    }

    public Object pull() {
	return outoforder.first();
    }
}
