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

import COM.claymoresystems.ptls.*;
import seda.sandStorm.lib.aSocket.aSocketInputStream;
import seda.sandStorm.lib.aTLS.protocol.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;

/**
 * An aTLSPacketReader is associated with each aTLSConnection to help order the
 * ATcpInPackets and to group packets into entire records if a record happens
 * to be broken up into multiple packets. Also handles multiple records in
 * one packet.
 */
class aTLSPacketReader implements aTLSConst{
    private static final boolean DEBUG = false;
    private static final boolean DEBUGBYTES = false;

    // need_length indicates not enough data to read the length
    // need_data indicates that the length of the data is known, but
    // that amount of data has not been received.
    static final int NEED_LENGTH = 1;
    static final int NEED_DATA = 2;
    static final int NO_DATA = 0;

    int amountNeeded;
    int state;    
    
    // MULTITHREADING CHANGE!
    aSocketInputStream asis;
    
    // this is the record header information that is necessary
    SSLuint8 type;
    SSLuint16 version;
    SSLopaque data;

    aTLSConnection atlsconn;

    // this is for sslv2 stuff
    byte[] checkByte = new byte[2];
    boolean sslv2 = false;
    SSLuint16 sslv2_len = new SSLuint16();
   
    public aTLSPacketReader (aTLSConnection atlsconn) {
	this.atlsconn = atlsconn;
	asis = new aSocketInputStream();
	reset();
    }

    public void reset() {
	amountNeeded = 0;
	state = NO_DATA;
	checkByte[0] = -1;
    }

    /**
     * This function will return a linkedList consisting of all the records that are in the
     * aSocketInputStream. Need a linkedlist because there could be multiple records in one packet.
     * Every record (except SSLv2 client hellos) has 5 bytes at the beginning:
     * 1st byte indicates the record type: application data, handshake, change cipher spec, alert.
     * 2nd & 3rd bytes indicate the protocol version: SSL, TLS, etc...
     * 4th & 5th bytes indicate the length of the data.
     * For SSLv2 client hello, need to read the first 2 bytes to get the length of the message.
     *
     * No need to decode the record header until there are at least 5 bytes read in initially.
     * Once those 5 bytes have been read, create an aTLSRecord, decode the header to get the 
     * information, then wait for all that data to come in.
     *
     * Assuming that two completely different messages will not exist on the same ATcpInPacket
     * So if I need 60 bytes, and it was broken up into 9, 9, 9, 9, 9, 9, and the last packet
     * only needed 4 bytes, another packet wouldn't add on to that 4 bytes.
     */
    public LinkedList readPacket() throws java.io.IOException {
	LinkedList recordHolder = new LinkedList();

	boolean leave = false;
	if (DEBUG) System.err.println ("aTLSPacketReader: This is how much is in the asis stream now: " + asis.available());

	// while loop is necessary b/c need to keep looping through the data if there are multiple records in one packet
	while (asis.available() > 0) {
	    switch (state) {
	    case NO_DATA:
		// the only purpose for this is for the SSLv2ClientHello
		// need to read the first byte to see if it matches an SSLv2 client hello, or just garbage
		if ((atlsconn.isServer) && (atlsconn.conn.hs.state == SSL_HS_WAIT_FOR_CLIENT_HELLO)) {
		    asis.read(checkByte, 0 , 1);
		    if (DEBUG) System.err.println ("aTLSPacketReader: this is what the first byte is: " + checkByte[0]);
		    if (checkByte[0] != 0x16) {
			if (DEBUG) System.err.println ("aTLSPacketReader: detected an SSLV2 message");
			sslv2 = true;
		    }
		    // else, it is a normal client hello, so drop down to next case
		}
	    case NEED_LENGTH:
		if (sslv2) {
		    // Need at least 2 bytes to read the length off of an sslv2
		    // remember, already read one byte, so only need to check if there's another
		    if (asis.available() >= 1) {
			if (DEBUG) System.err.println ("aTLSPacketReader: enough of the packet to read the length");
			asis.read (checkByte, 1, 1);
			try {
			    // there is no recordHeader for an SSLv2 client hello, so no need to call read record headeer.
			    sslv2_len.decode(atlsconn.conn, new ByteArrayInputStream (checkByte)); // Assume 2-byte header form 
			}
			catch (IOException e) {
			    // IMPORTANT:
			    // this short read should occur because sslv2_len.decode will decode the length, then decode the actual
			    // data, but since only supplied two bytes to get the length, a short read will occur.
			    if (DEBUG) System.err.println ("aTLSPacketReader: This short read is expected for SSLv2, read comments.");
			}
			amountNeeded = sslv2_len.value & 0x7fff;
			if (DEBUG) System.err.println ("aTLsPacketReader: will need this much for sslv2: " + amountNeeded);
			// this will drop down to the next case, because enough to read header now.
		    }
		    else {
			leave = true;
			state = NEED_LENGTH;
			break;
		    }
		}
		else {
		    // need at least 5 bytes to read off the correct information for a non sslv2 handshake message
		    if (asis.available() >= 5) {
			if (DEBUG) System.err.println ("aTLSPacketReader: Enough data to read record length. " + 
						       "This is how much that is available: " + asis.available());
			readRecordHeader();
			// this will drop down to the next case, because there is no break;
		    }
		    else {
			if (DEBUG) System.err.println ("aTLSPacketReader: Not enough data to read record length. " + 
						   " This is how much that is available: " + asis.available() + "\n\n\n");
			// this boolean is necessary b/c this is the only way to break out of the while loop, even though
			// there is some data available because we want to exit to wait for another packet with more info.
			leave = true;
			state = NEED_LENGTH;
			break;
		    }
		}
	    case NEED_DATA:
		if (asis.available() < amountNeeded) {
		    // so not enough data yet
		    if (DEBUG) System.err.println ("aTLSPacketReader: All the data has not been received yet.");
		    if (DEBUG) System.err.println ("Need to read this much: " + amountNeeded + 
						   ", but only have this much: " + asis.available());
		    state = NEED_DATA;
		    // need to set this because need to break out of the while loop
		    leave = true;
		}
		else {
		    if (DEBUG) System.err.println ("aTLSPacketReader: All data has been received, about to start reading this much: " 
						   + asis.available());
		    // all the necessary data has been received, so can read all of it now
		    byte[] needed;
		    int temp;
		    aTLSRecord record;
		    if (sslv2) {
			needed = new byte [amountNeeded + 2];
			temp = asis.read (needed, 2, amountNeeded);

			if (temp != amountNeeded) {
			    System.err.println ("aTLSPacketReader: didn't read enough, something wrong here. Error, contact mdw@cs.berkeley.edu");
			}

			needed[0] = checkByte[0];
			needed[1] = checkByte[1];

			record = new aTLSHandshakeRecord (needed, true);
		    }
		    else {
			needed = new byte [amountNeeded];
			temp = asis.read (needed);
			if (temp != amountNeeded) {
			    System.err.println ("aTLSPacketReader: didn't read enough, something wrong here. Error, contact mdw@cs.berkeley.edu");
			}
		    			
			data.value = needed;
			
			// need to call createRecord to "encode" the first 5 bytes back onto the record.
			record = createRecord();
		    }
		    
		    sslv2 = false;
		    
		    // just add the record to the Linked list, and loop again if there is more info
		    recordHolder.add (record);
		    // since one record was read, we want to see if we should read another, so we keep the 
		    // stream intact, but we reset the state
		    state = NO_DATA;
		    amountNeeded = 0;
		}	
		break;
	    default:
		System.err.println ("aTLSPacketReader: Bad state. Error, contact mdw@cs.berkeley.edu");
	    }
	    if (leave == true) {
		break;
	    }
	}
	// will return null if not all the data was received
	// otherwise, would have been returned within else of NEED_DATA case above
	if (asis.available() == 0) {
	    //System.err.println ("ATLSPACKETREADER: RESETTING EVERYTHING");
	    // the only time to reset is if all the information has been read
	    reset();
	}
	return recordHolder;
    }   

    /**
     * This function will be called when there is at least 5 bytes of data, meaning that 
     * there is enough data to read the record header information. 
     * Will create an aTLSRecord and call decodeHeader() to get the necessary
     * information to continue.
     * NOTE: not technically "decoding" the record, because the first 5 bytes are not
     * encrypted.
     */
    void readRecordHeader() throws java.io.IOException {
	if (DEBUG) System.err.println ("aTLSPacketReader: Inside readRecordHeader()");

	// need to reinitialize these, b/c readRecordHeader could be called for multiple records, and the
	// length information gets altered when the decoding takes place
	type = new SSLuint8();
	version = new SSLuint16();
	data = new SSLopaque(-65535);

	byte[] header = new byte[5];
	int temp;

	// this will read in the 5 bytes needed from the record header
	if (checkByte[0] != -1) {
	    // this is necessary because if not a sslv2 client hello, but it is a client hello message, then
	    // already read one byte off, so have to keep track of that byte.
	    header[0] = checkByte[0];
	    temp = asis.read(header, 1, 4);
	    if (temp != 4) {
		System.err.println ("aTLSPacketReader0: Error, didn't read enough in readRecordHeader(). Contact mdw@cs.berkeley.edu");
		System.err.println ("Ended up reading this much: " + temp + " when I needed 4");
	    }
	}
	else {
	    temp = asis.read (header);
	    if (temp != 5) {
		System.err.println ("aTLSPacketReader1: Error, didn't read enough in readRecordHeader(). Contact mdw@cs.berkeley.edu");
		System.err.println ("Ended up reading this much: " + temp + " when I needed 5");
	    }
	}
	
	if (DEBUG) System.err.println ("aTLSPacketReader: This is what the record header looks like ");
	if (DEBUG) {
	    for (int m = 0; m < 5; m++) {
		System.err.print (header[m] + " ");
	    }
	}
	if (DEBUG) System.err.println ();
	if (DEBUG) System.err.println ("aTLSPacketReader: Just read off the header, this is how much is available: " + asis.available());
	
	ByteArrayInputStream bais = new ByteArrayInputStream (header);

	type.decode(atlsconn.conn, bais);
	version.decode(atlsconn.conn, bais);
	try {
	    data.decode(atlsconn.conn, bais);
	}
	catch (IOException e) {
	    // IMPORTANT: 
	    // this will throw an exception, because when we decode the data, all we want is the length,
	    // but data.decode will try to decode all of the data. But since we only call decode with two
	    // bytes in the stream, a short read will always happen, BUT THAT'S EXPECTED
	    if (DEBUG) System.err.println ("aTLSPacketReader: this IO problem for short read is expected, read comments.");
	}

	amountNeeded = data.value.length;
	if (DEBUG) System.err.println ("aTLSPacketReader: Will have to read this much to get the whole record: " + amountNeeded);
    }
    
    /**
     * Returns the appropriate aTLSRecord type (change cipher spec, alert, handshake, application data) after
     * "reencoding" the first few bytes.
     */
    aTLSRecord createRecord() {
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	
	try {
	    type.encode (atlsconn.conn, baos);
	    version.encode (atlsconn.conn, baos);
	    data.encode (atlsconn.conn, baos);
	}
	catch (Exception e) {
	    System.err.println ("aTLSPacketReader: Exception trying to re-encode the record: " + e + 
				" Internal Error, contact mdw@cs.berkeley.edu");
	}

	if (DEBUGBYTES) {
	    System.err.println ("aTLSPacketReader: this is the data after encoded again.");
	    byte[] x = baos.toByteArray();
	    for (int i = 0; i < x.length; i++) {
		System.err.print (x[i] + " ");
	    }
	    System.err.println ();
	}
	
	switch (type.value) {
	case SSL_CT_CHANGE_CIPHER_SPEC:
	    return (new aTLSCipherSpecRecord(baos.toByteArray()));
	case SSL_CT_ALERT:
	    return (new aTLSAlertRecord(baos.toByteArray()));
	case SSL_CT_HANDSHAKE:
	    return (new aTLSHandshakeRecord(baos.toByteArray()));
	case SSL_CT_APPLICATION_DATA:
	    return (new aTLSAppDataRecord(baos.toByteArray(), data.value.length));
	default:
	    System.err.println ("aTLSPacketReader: Not a valid record type: " + type.value + " Internal Error, contact mdw@cs.berkeley.edu");
	    return null;
	}
    }
}




