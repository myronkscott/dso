/* 
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at 
 *
 *      http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Terracotta Platform.
 *
 * The Initial Developer of the Covered Software is 
 *      Terracotta, Inc., a Software AG company
 */
package com.tc.objectserver.tx;

import com.tc.l2.ha.TransactionBatchListener;
import com.tc.net.NodeID;
import com.tc.object.msg.CommitTransactionMessage;
import com.tc.object.tx.TransactionID;

public interface TransactionBatchManager {

  public void addTransactionBatch(CommitTransactionMessage ctm);

  public void defineBatch(NodeID node, int numTxns) throws BatchDefinedException;

  public boolean batchComponentComplete(NodeID committerID, TransactionID txnID) throws NoSuchBatchException;

  public void nodeConnected(NodeID nodeID);

  public void shutdownNode(NodeID nodeID);

  public void processTransactions(TransactionBatchContext batchContext);

  public void notifyServerHighWaterMark(NodeID nodeID, long serverHighWaterMark);
  
  public void registerForBatchTransaction(TransactionBatchListener listener);

}