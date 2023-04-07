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
 * This file provides a set of utility functions implementing a B-Tree.
 * This is useful for doing a fast search of a set of file descriptors.
 */

#ifndef _MDW_BTREE_H
#define _MDW_BTREE_H

#define BTREE_DEBUG(_x) 

#define BTREE_PARAM_M 3 

typedef struct _btree_node {
  int numkeys;  
  int keys[(2*BTREE_PARAM_M)-1];
  void *data[(2*BTREE_PARAM_M)-1];
  struct _btree_node *children[2*BTREE_PARAM_M];
  int leaf;
} btree_node;

static inline void btree_dump(btree_node *node, int indent) {
  int i, n;
  for (i = 0; i < node->numkeys; i++) { 
    for (n = 0; n < indent; n++) fprintf(stderr," ");
    fprintf(stderr,"keys[%d] is %d\n", i, node->keys[i]);
  }
  if (node->leaf == 0) {
    for (i = 0; i < node->numkeys+1; i++) { 
      for (n = 0; n < indent; n++) fprintf(stderr," ");
      fprintf(stderr,"children[%d]:\n", i);
      btree_dump(node->children[i], indent+4);
    }
  }
}

static inline void *btree_search(btree_node *node, int key) {
  int i = 0;

  BTREE_DEBUG(btree_dump(node, 1));

  BTREE_DEBUG(fprintf(stderr,"btree_search: key %d\n", key));
  while ((i < node->numkeys) && (key > node->keys[i])) {
    BTREE_DEBUG(fprintf(stderr,"btree_search: node->keys[%d] is %d\n", i, node->keys[i]));
    i++;
  }
  if ((i < node->numkeys) && (key == node->keys[i])) {
    BTREE_DEBUG(fprintf(stderr,"btree_search: exact match found at %d\n", i));
    return node->data[i];
  }
  if (node->leaf != 0) {
    BTREE_DEBUG(fprintf(stderr,"btree_search: not found and leaf\n"));
    return NULL;
  }
  // XXX Should be node->children[i-1]?
  BTREE_DEBUG(fprintf(stderr,"btree_search: recursing on children[%d]\n", i));
  return btree_search(node->children[i], key);
}

static inline btree_node *btree_newnode() {
  btree_node *node = (btree_node *)malloc(sizeof(btree_node));
  if (node == NULL) {
    fprintf(stderr,"Warning: Cannot allocate new btree_node!\n");
  }
  node->numkeys = 0;
  node->leaf = 1;
  return node;
}

static inline void btree_split_child(btree_node *node, int index, btree_node *child) {
  btree_node *newnode = btree_newnode();
  int j;

  BTREE_DEBUG(fprintf(stderr,"btree_split_child: index %d (leaf=%d)\n", index, node->leaf));
  newnode->leaf = child->leaf;
  BTREE_DEBUG(fprintf(stderr,"newnode->leaf is %d\n", newnode->leaf));
  newnode->numkeys = BTREE_PARAM_M-1;
  BTREE_DEBUG(fprintf(stderr,"newnode->numkeys is %d\n", newnode->numkeys));

  BTREE_DEBUG(fprintf(stderr,"moving child keys to newnode (leaf=%d)\n", node->leaf));
  for (j = 0; j < BTREE_PARAM_M-1; j++) {
    newnode->keys[j] = child->keys[j+BTREE_PARAM_M];
    newnode->data[j] = child->data[j+BTREE_PARAM_M];
  }
  if (child->leaf == 0) {
    BTREE_DEBUG(fprintf(stderr,"moving child children to newnode (leaf=%d)\n", node->leaf));
    for (j = 0; j < BTREE_PARAM_M; j++) {
      newnode->children[j] = child->children[j+BTREE_PARAM_M];
    }
  }
  child->numkeys = BTREE_PARAM_M-1;
  BTREE_DEBUG(fprintf(stderr,"shifting node->children right (leaf=%d)\n", node->leaf));
  // XXX Should be j >= index+1?
  for (j = node->numkeys; j >= index; j--) {
    node->children[j+1] = node->children[j];
  }
  BTREE_DEBUG(fprintf(stderr,"adding newnode to node->children at %d (leaf=%d)\n", index+1, node->leaf));
  node->children[index+1] = newnode;
  BTREE_DEBUG(fprintf(stderr,"shifting node->keys right, %d keys (leaf=%d)\n", node->numkeys, node->leaf));
  for (j = node->numkeys; j >= index+1; j--) {
    node->keys[j] = node->keys[j-1];
    node->data[j] = node->data[j-1];
  }
  BTREE_DEBUG(fprintf(stderr,"adding child->keys[%d] to node->keys[%d] (val %d)\n", BTREE_PARAM_M-1, index, child->keys[BTREE_PARAM_M-1]));
  node->keys[index] = child->keys[BTREE_PARAM_M-1];
  node->data[index] = child->data[BTREE_PARAM_M-1];
  node->numkeys++;
  BTREE_DEBUG(fprintf(stderr,"split done, leaf=%d\n", node->leaf));

  BTREE_DEBUG(fprintf(stderr,"After splitting child, subtree: ****\n"));
  BTREE_DEBUG(btree_dump(node, 5));
  BTREE_DEBUG(fprintf(stderr,"**********\n"));
}

static inline void btree_insert_nonfull(btree_node *node, int key, void *data) {
  int i = node->numkeys-1;

  BTREE_DEBUG(fprintf(stderr,"btree_insert_nonfull: key %d, node->numkeys %d\n", key, node->numkeys));

  if (node->leaf == 1) {
    BTREE_DEBUG(fprintf(stderr,"btree_insert_nonfull: node is leaf\n"));
    BTREE_DEBUG(fprintf(stderr,"btree_insert_nonfull: shifting keys right\n"));
    while ((i >= 0) && (key < node->keys[i])) {
      node->keys[i+1] = node->keys[i];
      node->data[i+1] = node->data[i];
      i--;
    }
    BTREE_DEBUG(fprintf(stderr,"btree_insert_nonfull: inserting at keys[%d]\n",i+1));
    node->keys[i+1] = key;
    node->data[i+1] = data;
    node->numkeys++;

  } else {
    btree_node *child;
    BTREE_DEBUG(fprintf(stderr,"btree_insert_nonfull: node is not a leaf\n"));
    while ((i >= 0) && (key < node->keys[i])) {
      i--;
    }
    i++;
    child = node->children[i];
    BTREE_DEBUG(fprintf(stderr,"btree_insert_nonfull: child is children[%d]\n", i));

    if (child->numkeys == (2*BTREE_PARAM_M)-1) {
      BTREE_DEBUG(fprintf(stderr,"btree_insert_nonfull: child full, splitting\n"));
      btree_split_child(node, i, child);

      BTREE_DEBUG(fprintf(stderr,"btree_insert_nonfull: After splitting child, tree is now: -----------\n"));
      BTREE_DEBUG(btree_dump(node, 1));
      BTREE_DEBUG(fprintf(stderr,"----------------------\n"));

      if (key > node->keys[i]) i++;
    }
    BTREE_DEBUG(fprintf(stderr,"btree_insert_nonfull: inserting into child [%d]\n", i));
    btree_insert_nonfull(node->children[i], key, data);
  }
}

// Insert a key into the tree; return new root of tree
static inline btree_node *btree_insert(btree_node *tree, int key, void *data) {
  btree_node *node, *origroot;

  BTREE_DEBUG(fprintf(stderr,"btree_insert: inserting key %d\n", key));
  BTREE_DEBUG(btree_dump(tree, 1));

  if (tree->numkeys == (2*BTREE_PARAM_M)-1) {
    node = btree_newnode();
    node->leaf = 0;
    origroot = tree;
    node->children[0] = origroot;
    btree_split_child(node, 0, origroot);
    btree_insert_nonfull(node, key, data);
    return node;
  } else {
    btree_insert_nonfull(tree, key, data);
    return tree;
  }
}


#endif /* _MDW_BTREE_H */
