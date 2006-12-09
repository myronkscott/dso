/*
 * All content copyright (c) 2003-2006 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tc.net.protocol.delivery;

import com.tc.net.protocol.TCNetworkMessage;

/**
 * Message at the OAOO protocol level
 */
public interface OOOProtocolMessage extends TCNetworkMessage {
  
  public long getAckSequence();

  public long getSent();

  public boolean isAckRequest();

  public boolean isSend();
}