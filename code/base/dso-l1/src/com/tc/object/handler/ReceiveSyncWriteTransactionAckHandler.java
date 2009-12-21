/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.tc.object.handler;

import com.tc.async.api.AbstractEventHandler;
import com.tc.async.api.EventContext;
import com.tc.object.msg.SyncWriteTransactionRecvdMessage;
import com.tc.object.tx.RemoteTransactionManager;
import com.tc.object.tx.TxnBatchID;

public class ReceiveSyncWriteTransactionAckHandler extends AbstractEventHandler {
  private final RemoteTransactionManager remoteTransactionManager;

  public ReceiveSyncWriteTransactionAckHandler(RemoteTransactionManager remoteTransactionManager) {
    this.remoteTransactionManager = remoteTransactionManager;
  }

  @Override
  public void handleEvent(EventContext context) {
    SyncWriteTransactionRecvdMessage msg = (SyncWriteTransactionRecvdMessage) context;
    TxnBatchID batchID = new TxnBatchID(msg.getBatchID());
    remoteTransactionManager.batchReceived(batchID);
  }
}
