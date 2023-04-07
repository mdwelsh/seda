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

static jclass _mdw_exceptions_exc_class;
static char * _mdw_exceptions_msg;

#ifdef _WIN32

	#define THROW_WIN_EXCEPTION(ENV, EXCEPTION_TYPE, MESSAGE) { \
    char __err_msg[256]; \
    int __err_type = MESSAGE; /* Force evaluation */ \
    FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM, NULL, __err_type, 0, __err_msg, 256, NULL); \
	THROW_EXCEPTION(ENV, EXCEPTION_TYPE, __err_msg); \
}

#endif

#define THROW_EXCEPTION(ENV, EXCEPTION_TYPE, MESSAGE) \
  _mdw_exceptions_msg = MESSAGE; /* Force evaluation */ \
  _mdw_exceptions_exc_class = (*ENV)->FindClass(ENV, EXCEPTION_TYPE); \
  if (_mdw_exceptions_exc_class != NULL) (*ENV)->ThrowNew(ENV, _mdw_exceptions_exc_class, MESSAGE);

#define EXC_IF_NOTOK_VOIDRET(EXPR, ENV, EXCEPTION_TYPE, MESSAGE) { \
  if (!(EXPR)) { THROW_EXCEPTION(ENV, EXCEPTION_TYPE, MESSAGE); return; } \
} 

#define EXC_IF_NOTOK(EXPR, ENV, EXCEPTION_TYPE, MESSAGE, RETVAL) { \
  if (!(EXPR)) { THROW_EXCEPTION(ENV, EXCEPTION_TYPE, MESSAGE); return RETVAL; } \
}






