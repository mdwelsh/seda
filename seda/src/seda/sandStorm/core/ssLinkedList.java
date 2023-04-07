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

package seda.sandStorm.core;

/**
 * The ssLinkedList class is just that - a linked list abstraction that
 * supports very efficient insertion and deletion, as well as an
 * Enumeration interface.  None of the methods in this linked list are
 * synchronized.  If you want synchronization, do it yourself.
 *
 * @author   Steve Gribble
 */
public class ssLinkedList {

  private int num_in_list;
  private ssLinkedListElement first;
  private ssLinkedListElement last;

  // some private variables to maintain a heap of elements for fast
  // allocation
  private ssLinkedListElement lle_heap[];
  private int num_in_lle_heap;
  private static final int HEAP_ALLOC_NUM = 5;

  /**
   * This inner class is the chaining mechanism for the linked list
   */
  private class ssLinkedListElement {
    public Object obj;
    protected ssLinkedListElement prev, next;

    public ssLinkedListElement(Object o) {
      prev = next = null;
      obj = o;
    }

  }

  /**
   * A ssLinkedListEnumeration is a java.util.Enumeration over the
   * ssLinkedList elements.
   *
   * @see java.util.Enumeration
   */
  public class ssLinkedListEnumeration implements java.util.Enumeration {
    private ssLinkedListElement curElement;

    public ssLinkedListEnumeration() {
      curElement = ssLinkedList.this.first;
    }

    // the enumeration methods
    public boolean hasMoreElements() {
      if (curElement == null)
	return false;
      return true;
    }
    public Object nextElement() throws java.util.NoSuchElementException {
      Object retme;

      if (curElement == null)
	throw new java.util.NoSuchElementException();
      retme = curElement.obj;
      curElement = curElement.next;
      return retme;
    }
  }

  private static ssLinkedListEqualityComparator ll_equality_comparator = 
    new ssLinkedListEqualityComparator();


  /**
   * Allocates a brand new ssLinkedList
   */
  public ssLinkedList() {
    num_in_list = 0;
    first = last = null;
    lle_heap = new ssLinkedListElement[HEAP_ALLOC_NUM];
    num_in_lle_heap = 0;
  }


  // heap o' elements - we'll try to keep no more than 25 cached

  private ssLinkedListElement alloc_lle(Object o) {
    ssLinkedListElement retel;

    if (num_in_lle_heap == 0) {
      for (int i=0; i<HEAP_ALLOC_NUM; i++) {
	lle_heap[i] = new ssLinkedListElement(null);
	num_in_lle_heap++;
      }
    }

    num_in_lle_heap--;
    retel = lle_heap[num_in_lle_heap];
    retel.obj = o;
    return retel;
  }

  private void free_lle(ssLinkedListElement lle) {
    if (num_in_lle_heap < HEAP_ALLOC_NUM) {
      lle_heap[num_in_lle_heap] = lle;
      num_in_lle_heap++;
      lle.next = lle.prev = null;
      lle.obj = null;
    }
  }

  /**
   * Returns the number of elements in the list
   *
   * @return number of elements in the list
   */
  public int size() {
    return num_in_list;
  }

  /**
   * Adds an object to the tail (end) of the linked list.
   *
   * @param o  the object to add
   */
  public void add_to_tail(Object o) {
    ssLinkedListElement lle = alloc_lle(o);

    if (first == null) {
      first = last = lle;
    } else {
      last.next = lle;
      lle.prev = last;
      last = lle;
    }
    num_in_list++;
  }

  /**
   * Gets the tail object from the linked list.
   *
   * @return the tail, or null if there is nothing in the list.
   */
  public Object get_tail() {
    if (last == null)
      return null;
    return last.obj;
  }

  /**
   * Removes the tail object from the linked list, and returns it.
   *
   * @return the tail, or null if there is nothing in the list.
   */
  public Object remove_tail() {
    ssLinkedListElement retme = null;
    Object retobj = null;

    if (last == null)
      return null;
    retme = last;

    if (first == last) {
      first = last = null;
    } else {
      last = last.prev;
      last.next = null;
    }

    num_in_list--;
    retobj = retme.obj;
    free_lle(retme);
    return retobj;
  }

  /**
   * Adds an object to the head (start) of the linked list.
   *
   * @param o  the object to add
   */
  public void add_to_head(Object o) {
    ssLinkedListElement lle = alloc_lle(o);

    if (first == null) {
      first = last = lle;
    } else {
      first.prev = lle;
      lle.next = first;
      first = lle;
    }
    num_in_list++;
  }

  /**
   * Gets the head object from the linked list.
   *
   * @return the head, or null if there is nothing in the list.
   */
  public Object get_head() {
    if (first == null)
      return null;
    return first.obj;
  }

  /**
   * Removes the head object from the linked list, and returns it.
   *
   * @return the head, or null if there is nothing in the list.
   */
  public Object remove_head() {
    ssLinkedListElement retme = null;
    Object retobj = null;

    if (first == null)
      return null;
    retme = first;

    if (first == last) {
      first = last = null;
    } else {
      first = first.next;
      first.prev = null;
    }

    num_in_list--;
    retobj = retme.obj;
    free_lle(retme);
    return retobj;
  }

  public void remove_all() {
    num_in_list = 0;
    first = last = null;
    lle_heap = new ssLinkedListElement[HEAP_ALLOC_NUM];
    num_in_lle_heap = 0;
  }

  /**
   * Gets the first object to match according to the comparator
   * function.
   *
   * @return the matching object, or null if there is nothing that matches.
   */
  public Object get_comparator(Object known, ssLinkedListComparator llc) {
    ssLinkedListElement tmp;

    tmp = first;
    while (tmp != null) {
      if (llc.compare(known, tmp.obj)) {
	return tmp.obj;
      }
      tmp = tmp.next;
    }
    return null;
  }

  /**
   * Removes the first object to match according to the comparator function,
   * and returns it.
   *
   * @return the match, or null if there is nothing that matches.
   */
  public Object remove_comparator(Object known, ssLinkedListComparator llc) {
    ssLinkedListElement tmp;

    tmp = first;
    while (tmp != null) {
      if (llc.compare(known, tmp.obj)) {

	// cut it out and return it
	if (tmp == first) {
	  return this.remove_head();
	} else if (tmp == last) {
	  return this.remove_tail();
	} else {
	  // somewhere in middle
	  Object retobj;

	  (tmp.prev).next = tmp.next;
	  (tmp.next).prev = tmp.prev;
	  num_in_list--;
	  retobj = tmp.obj;
	  free_lle(tmp);
	  return retobj;
	}
      }
      tmp = tmp.next;
    }
    return null;
  }


  /**
   * Returns the first object that is "equal" to the given object,
   * based on the response of the Object.equals() method.
   **/
  public Object get_item(Object known) {
    return get_comparator(known, ll_equality_comparator);
  }

  /**
   * Removes the first object that is "equal" to the given object,
   * based on the response of the Object.equals() method.
   **/
  public Object remove_item(Object known) {
    return remove_comparator(known, ll_equality_comparator);
  }



  /**
   * Returns a java.util.Enumeration enumeration over the elements of the
   * linked list.  If you modify the list while enumerating over it,
   * you get what you deserve (i.e. undefined behaviour).
   *
   * @return the enumeration over the elements
   * @see java.util.Enumeration
   */
  public java.util.Enumeration elements() {
    return (java.util.Enumeration) (new ssLinkedListEnumeration());
  }


  /**
   * Return a string representation, for debugging purposes
   **/
  public String toString() {
    return toString("");
  }

  public String toString(String prefix) {
    String pre = prefix;
    if(pre == null) pre = "";

    String ret = pre + "ssLinkedList: length=" + num_in_list + "\n";

    ssLinkedListElement current = first;
    while( current != null ) {
      if(current.obj == null) {
        ret += pre + "  (null)\n";
      }
      
      else {
        if(current.obj instanceof ssLinkedList) {
          ret += pre + "LINKED LIST\n" +
            ((ssLinkedList)current.obj).toString(pre+"  ");
        }
        else {
          ret += pre + "  " + current.obj.toString() + "\n";
        }
      }

      current = current.next;
    }
    
    return ret;
  }



  // test code
  private static void printTime(long long1, long long3, int int5) {
    long long6 = long3 - long1;
    double double8 = (double) int5 / (double) long6;
    double double10 = double8 * 1000.0;
    
    System.out.println( int5 + " iterations in " + long6 + 
			" milliseconds = " + double10 
			+ " iterations per second" );
  }
  
  /**
   * Test code for the ssLinkedList
   */

  private static final int NUM_IT = 10000000;
  public static void main(String args[]) {
    ssLinkedList ll = new ssLinkedList();
    long before, after;

    for (int i=0; i<990; i++)
      ll.add_to_tail(null);

    before = System.currentTimeMillis();
    before = System.currentTimeMillis();
    for (int i=0; i<NUM_IT; i++) {
      ll.add_to_tail(null);
      ll.remove_head();
    }
    after = System.currentTimeMillis();
    printTime(before, after, NUM_IT);
  }

}


