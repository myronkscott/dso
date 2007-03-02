/*
 * All content copyright (c) 2003-2007 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.l2.objectserver;

import com.tc.async.api.Sink;
import com.tc.l2.context.SyncObjectsRequest;
import com.tc.l2.msg.ObjectListSyncMessage;
import com.tc.l2.msg.ObjectListSyncMessageFactory;
import com.tc.l2.msg.ObjectSyncMessage;
import com.tc.l2.state.StateManager;
import com.tc.logging.TCLogger;
import com.tc.logging.TCLogging;
import com.tc.net.groups.GroupEventsListener;
import com.tc.net.groups.GroupException;
import com.tc.net.groups.GroupManager;
import com.tc.net.groups.GroupMessage;
import com.tc.net.groups.GroupMessageListener;
import com.tc.net.groups.GroupResponse;
import com.tc.net.groups.NodeID;
import com.tc.object.msg.CommitTransactionMessage;
import com.tc.objectserver.api.ObjectManager;
import com.tc.util.Assert;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ReplicatedObjectManagerImpl implements ReplicatedObjectManager, GroupEventsListener, GroupMessageListener {

  private static final TCLogger      logger = TCLogging.getLogger(ReplicatedObjectManagerImpl.class);

  private final ObjectManager        objectManager;
  private final GroupManager         groupManager;
  private final StateManager         stateManager;
  private final L2ObjectStateManager l2ObjectStateManager;
  private final Sink                 objectsSyncSink;

  public ReplicatedObjectManagerImpl(GroupManager groupManager, StateManager stateManager,
                                     L2ObjectStateManager l2ObjectStateManager, ObjectManager objectManager,
                                     Sink objectsSyncSink) {
    this.groupManager = groupManager;
    this.stateManager = stateManager;
    this.objectManager = objectManager;
    this.objectsSyncSink = objectsSyncSink;
    this.l2ObjectStateManager = l2ObjectStateManager;
    this.groupManager.registerForGroupEvents(this);
    this.groupManager.registerForMessages(ObjectListSyncMessage.class, this);
    this.groupManager.registerForMessages(ObjectSyncMessage.class, this);
  }

  /**
   * This method is used to sync up all ObjectIDs from the remote ObjectManagers. It is synchronous and after when it
   * returns nobody is allowed to join the cluster with exisiting objects.
   */
  public void sync() {
    try {
      GroupResponse gr = groupManager.sendAllAndWaitForResponse(ObjectListSyncMessageFactory
          .createObjectListSyncRequestMessage());
      for (Iterator i = gr.getResponses().iterator(); i.hasNext();) {
        ObjectListSyncMessage msg = (ObjectListSyncMessage) i.next();
        add2L2StateManager(msg.messageFrom(), msg.getObjectIDs());
      }
    } catch (GroupException e) {
      logger.error(e);
      throw new AssertionError(e);
    }
  }

  public void incomingTransactions(CommitTransactionMessage ctm, List txns, Collection serverTxnIDs,
                                   Collection completedTxnIds) {
    // TODO
  }

  public void nodeJoined(NodeID nodeID) {
    if (stateManager.isActiveCoordinator()) {
      query(nodeID);
    }
  }

  // Query current state of the other L2
  private void query(NodeID nodeID) {
    try {
      groupManager.sendTo(nodeID, ObjectListSyncMessageFactory.createObjectListSyncRequestMessage());
    } catch (GroupException e) {
      logger.error("Error Writting Msg : ", e);
    }
  }

  public void nodeLeft(NodeID nodeID) {
    if (stateManager.isActiveCoordinator()) {
      l2ObjectStateManager.removeL2(nodeID);
    }
  }

  public void messageReceived(NodeID fromNode, GroupMessage msg) {
    if (msg instanceof ObjectListSyncMessage) {
      ObjectListSyncMessage clusterMsg = (ObjectListSyncMessage) msg;
      handleClusterObjectMessage(fromNode, clusterMsg);
    } else if (msg instanceof ObjectSyncMessage) {
      ObjectSyncMessage syncMsg = (ObjectSyncMessage) msg;
      objectsSyncSink.add(syncMsg);
    } else {
      throw new AssertionError("ReplicatedObjectManagerImpl : Received wrong message type :" + msg.getClass().getName()
                               + " : " + msg);

    }
  }

  private void handleClusterObjectMessage(NodeID nodeID, ObjectListSyncMessage clusterMsg) {
    try {
      switch (clusterMsg.getType()) {
        case ObjectListSyncMessage.REQUEST:
          handleObjectListRequest(nodeID, clusterMsg);
          break;
        case ObjectListSyncMessage.RESPONSE:
          handleObjectListResponse(nodeID, clusterMsg);
          break;

        default:
          throw new AssertionError("This message shouldn't have been routed here : " + clusterMsg);
      }
    } catch (GroupException e) {
      logger.error("Error handling message : " + clusterMsg, e);
      throw new AssertionError(e);
    }
  }

  private void handleObjectListResponse(NodeID nodeID, ObjectListSyncMessage clusterMsg) {
    Assert.assertTrue(stateManager.isActiveCoordinator());
    Set oids = clusterMsg.getObjectIDs();
    if (!oids.isEmpty()) {
      logger.error("Nodes joining the cluster after startup shouldnt have any Objects. " + nodeID + " contains "
                   + oids.size() + " Objects !!!");
      logger.error("Forcing node to Quit !!");
      groupManager.zapNode(nodeID);
    } else {
      add2L2StateManager(nodeID, oids);
    }
  }

  private void add2L2StateManager(NodeID nodeID, Set oids) {
    int missing = l2ObjectStateManager.setExistingObjectsList(nodeID, oids, objectManager);
    if (missing == 0) {
      stateManager.moveNodeToPassiveStandby(nodeID);
    } else {
      objectsSyncSink.add(new SyncObjectsRequest(nodeID));
    }
  }

  private void handleObjectListRequest(NodeID nodeID, ObjectListSyncMessage clusterMsg) throws GroupException {
    Assert.assertFalse(stateManager.isActiveCoordinator());
    Set knownIDs = objectManager.getAllObjectIDs();
    groupManager.sendTo(nodeID, ObjectListSyncMessageFactory.createObjectListSyncResponseMessage(clusterMsg, knownIDs));
  }
}
