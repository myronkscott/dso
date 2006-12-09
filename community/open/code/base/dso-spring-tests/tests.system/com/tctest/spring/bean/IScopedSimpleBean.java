/*
 * All content copyright (c) 2003-2006 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tctest.spring.bean;

public interface IScopedSimpleBean extends ISimpleBean {

  public String invokeDestructionCallback();
  
  public String getScopeId();
  
  public boolean isInClusteredSingletonCache();

}