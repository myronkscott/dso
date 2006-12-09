/*
 * All content copyright (c) 2003-2006 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tc.net.protocol.tcm;

import com.tc.exception.ImplementMe;

public class MockMessageChannelFactory implements ServerMessageChannelFactory {

  public MessageChannelInternal channel;
  public int                    callCount;

  public MessageChannelInternal createNewChannel(ChannelID id) {
    callCount++;
    return channel;
  }

  public TCMessageFactory getMessageFactory() {
    throw new ImplementMe();
  }

  public TCMessageRouter getMessageRouter() {
    throw new ImplementMe();
  }

}