/*
 * All content copyright (c) 2003-2006 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tctest.restart.system;

import com.tc.test.MultipleServersCrashMode;
import com.tc.test.MultipleServersPersistenceMode;
import com.tc.test.MultipleServersSharedDataMode;
import com.tc.test.activeactive.ActiveActiveTestSetupManager;
import com.tctest.ActiveActiveTransparentTestBase;
import com.tctest.TestConfigurator;
import com.tctest.TransparentTestIface;

public class ObjectDataDiskBasedActiveActiveTest extends ActiveActiveTransparentTestBase implements TestConfigurator {

  private int       clientCount  = 1;
  private final int electionTime = 5;
  
  public ObjectDataDiskBasedActiveActiveTest() {
//    disableAllUntil("2019-10-01");
  }

  protected Class<ObjectDataTestApp> getApplicationClass() {
    return ObjectDataTestApp.class;
  }

  public void doSetUp(TransparentTestIface t) throws Exception {
    t.getTransparentAppConfig().setClientCount(clientCount).setIntensity(1);
    t.initializeTestRunner();
  }

  public void setupActiveActiveTest(ActiveActiveTestSetupManager setupManager) {
    setupManager.setServerCount(4);
    setupManager.setServerCrashMode(MultipleServersCrashMode.NO_CRASH);
    setupManager.setServerShareDataMode(MultipleServersSharedDataMode.DISK);
    setupManager.setServerPersistenceMode(MultipleServersPersistenceMode.PERMANENT_STORE);
    setupManager.addActiveServerGroup(2, MultipleServersSharedDataMode.DISK, electionTime);
    setupManager.addActiveServerGroup(2, MultipleServersSharedDataMode.DISK, electionTime);
  }
}
