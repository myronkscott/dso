/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tctest.statistics;

import com.tc.management.JMXConnectorProxy;
import com.tc.statistics.StatisticData;
import com.tc.statistics.beans.StatisticsEmitterMBean;
import com.tc.statistics.beans.StatisticsMBeansNames;
import com.tc.statistics.beans.StatisticsManagerMBean;
import com.tc.statistics.retrieval.actions.SRAShutdownTimestamp;
import com.tc.statistics.retrieval.actions.SRAStartupTimestamp;
import com.tctest.TransparentTestBase;
import com.tctest.TransparentTestIface;

import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;

public class StatisticsManagerNoActionsTest extends TransparentTestBase {
  protected void duringRunningCluster() throws Exception {
    JMXConnectorProxy jmxc = new JMXConnectorProxy("localhost", getAdminPort());
    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

    StatisticsManagerMBean stat_manager = (StatisticsManagerMBean)MBeanServerInvocationHandler
        .newProxyInstance(mbsc, StatisticsMBeansNames.STATISTICS_MANAGER, StatisticsManagerMBean.class, false);
    StatisticsEmitterMBean stat_emitter = (StatisticsEmitterMBean)MBeanServerInvocationHandler
        .newProxyInstance(mbsc, StatisticsMBeansNames.STATISTICS_EMITTER, StatisticsEmitterMBean.class, false);

    List data = new ArrayList();
    CollectingNotificationListener listener = new CollectingNotificationListener();
    mbsc.addNotificationListener(StatisticsMBeansNames.STATISTICS_EMITTER, listener, null, data);
    stat_emitter.enable();

    long sessionid = stat_manager.createCaptureSession();

    // register all the supported statistics
    String[] statistics = stat_manager.getSupportedStatistics();
    for (int i = 0; i < statistics.length; i++) {
      stat_manager.enableStatistic(sessionid, statistics[i]);
    }

    // remove all statistics
    stat_manager.disableAllStatistics(sessionid);

    // start capturing
    stat_manager.startCapturing(sessionid);

    // wait for 10 seconds
    Thread.sleep(10000);

    // stop capturing and wait for the last data
    synchronized (listener) {
      stat_manager.stopCapturing(sessionid);
      while (!listener.getShutdown()) {
        listener.wait(2000);
      }
    }

    // disable the notification and detach the listener
    stat_emitter.disable();
    mbsc.removeNotificationListener(StatisticsMBeansNames.STATISTICS_EMITTER, listener);

    // check the data
    assertEquals(2, data.size());
    assertEquals(SRAStartupTimestamp.ACTION_NAME, ((StatisticData)data.get(0)).getName());
    assertEquals(SRAShutdownTimestamp.ACTION_NAME, ((StatisticData)data.get(1)).getName());
  }

  protected Class getApplicationClass() {
    return StatisticsManagerNoActionsTestApp.class;
  }

  public void doSetUp(TransparentTestIface t) throws Exception {
    t.getTransparentAppConfig().setClientCount(StatisticsManagerNoActionsTestApp.NODE_COUNT);
    t.initializeTestRunner();
  }
}