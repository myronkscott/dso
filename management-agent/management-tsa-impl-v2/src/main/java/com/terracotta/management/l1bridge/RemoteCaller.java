/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.terracotta.management.l1bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.management.ServiceExecutionException;
import org.terracotta.management.l1bridge.RemoteCallDescriptor;
import org.terracotta.management.resource.Representable;

import com.terracotta.management.security.ContextService;
import com.terracotta.management.security.RequestTicketMonitor;
import com.terracotta.management.security.UserService;
import com.terracotta.management.service.RemoteAgentBridgeService;
import com.terracotta.management.service.TimeoutService;
import com.terracotta.management.user.UserInfo;
import com.terracotta.management.web.utils.TSAConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author Ludovic Orban
 */
public class RemoteCaller {

  private final RemoteAgentBridgeService remoteAgentBridgeService;
  private final ContextService contextService;
  private final ExecutorService executorService;
  private final RequestTicketMonitor requestTicketMonitor;
  private final UserService userService;
  private final TimeoutService timeoutService;
  private static final Logger LOG = LoggerFactory.getLogger(RemoteCaller.class);


  public RemoteCaller(RemoteAgentBridgeService remoteAgentBridgeService, ContextService contextService,
                      ExecutorService executorService, RequestTicketMonitor ticketMonitor,
                      UserService userService, TimeoutService timeoutService) {
    this.remoteAgentBridgeService = remoteAgentBridgeService;
    this.contextService = contextService;
    this.executorService = executorService;
    this.requestTicketMonitor = ticketMonitor;
    this.userService = userService;
    this.timeoutService = timeoutService;
  }

  public Set<String> getRemoteAgentNodeNames() throws ServiceExecutionException {
    return remoteAgentBridgeService.getRemoteAgentNodeNames();
  }

  public Map<String, Map<String, String>> getRemoteAgentNodeDetails() throws ServiceExecutionException {
    Map<String, Map<String, String>> nodes = new HashMap<String, Map<String, String>>();
    Set<String> remoteAgentNodeNames = remoteAgentBridgeService.getRemoteAgentNodeNames();
    Collection<Future<Map<String, Map<String, String>>>> futures = new ArrayList<Future<Map<String, Map<String, String>>>>();

    for (final String remoteAgentNodeName : remoteAgentNodeNames) {
      try {
        Future<Map<String, Map<String, String>>> future = executorService.submit(new Callable<Map<String, Map<String, String>>>() {
          @Override
          public Map<String, Map<String, String>> call() throws Exception {
            return Collections.singletonMap(remoteAgentNodeName, remoteAgentBridgeService.getRemoteAgentNodeDetails(remoteAgentNodeName));
          }
        });
        futures.add(future);
      } catch (RejectedExecutionException ree) {
        LOG.debug("L1 thread pool rejected task, throttling a bit before resuming fan-out call...", ree);
        try {
          Thread.sleep(100L);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
    }

    long timeLeft = timeoutService.getCallTimeout();
    int failedRequests = 0;

    for (Future<Map<String, Map<String, String>>> future : futures) {
      long before = System.nanoTime();
      try {
        Map<String, Map<String, String>> attributes = future.get(Math.max(1L, timeLeft), TimeUnit.MILLISECONDS);
        nodes.putAll(attributes);
      } catch (Exception e) {
        future.cancel(true);
        failedRequests++;
        LOG.debug("Future execution error in getRemoteAgentNodeDetails", e);
      } finally {
        timeLeft -= TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
      }
    }
    if (failedRequests > 0) {
      LOG.warn(failedRequests + "/" + futures.size() + " agent(s) failed to respond to getRemoteAgentNodeDetails");
    }

    return nodes;
  }

  public Object call(final String node, String serviceName, Method method, Object[] args) throws ServiceExecutionException {
    String ticket = requestTicketMonitor.issueRequestTicket();
    String token = userService.putUserInfo(contextService.getUserInfo());

    final RemoteCallDescriptor remoteCallDescriptor = new RemoteCallDescriptor(ticket, token, TSAConfig.getSecurityCallbackUrl(),
        serviceName, method.getName(), method.getParameterTypes(), args);

    Future<byte[]> future;
    try {
      future = executorService.submit(new Callable<byte[]>() {
        @Override
        public byte[] call() throws Exception {
          return remoteAgentBridgeService.invokeRemoteMethod(node, remoteCallDescriptor);
        }
      });
    } catch (RejectedExecutionException ree) {
      throw new ServiceExecutionException("Error invoking remote method ", ree);
    }

    byte[] bytes;
    try {
      bytes = future.get();
    } catch (Exception e) {
      future.cancel(true);
      throw new ServiceExecutionException("Error invoking remote method ", e);
    }

    try {
      return deserializeAndRewriteAgentId(bytes, node);
    } catch (IOException ioe) {
      throw new ServiceExecutionException("Error deserializing remote response", ioe);
    } catch (ClassNotFoundException cnfe) {
      throw new ServiceExecutionException("Error mapping remote response to local class", cnfe);
    }
  }

  public <T extends Representable> Collection<T> fanOutCollectionCall(final String serviceAgency, Set<String> nodes, final String serviceName, final Method method, final Object[] args) throws ServiceExecutionException {
    final UserInfo userInfo = contextService.getUserInfo();
    Collection<Future<Collection<T>>> futures = new ArrayList<Future<Collection<T>>>();

    for (final String node : nodes) {
      try {
        Future<Collection<T>> future = executorService.submit(new Callable<Collection<T>>() {
          @Override
          public Collection<T> call() throws Exception {
            String ticket = requestTicketMonitor.issueRequestTicket();
            String token = userService.putUserInfo(userInfo);

            if (serviceAgency != null) {
              Map<String, String> nodeDetails = remoteAgentBridgeService.getRemoteAgentNodeDetails(node);
              String nodeAgency = nodeDetails.get("Agency");
              if (!nodeAgency.equals(serviceAgency)) {
                return Collections.emptySet();
              }
            }

            RemoteCallDescriptor remoteCallDescriptor = new RemoteCallDescriptor(ticket, token, TSAConfig.getSecurityCallbackUrl(),
                serviceName, method.getName(), method.getParameterTypes(), args);
            byte[] bytes = remoteAgentBridgeService.invokeRemoteMethod(node, remoteCallDescriptor);
            return deserializeAndRewriteAgentId(bytes, node);
          }
        });
        futures.add(future);
      } catch (RejectedExecutionException ree) {
        LOG.debug("L1 thread pool rejected task, throttling a bit before resuming fan-out call...", ree);
        try {
          Thread.sleep(100L);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
    }


    long timeLeft = timeoutService.getCallTimeout();
    Collection<T> globalResult = new ArrayList<T>();
    int failedRequests = 0;

    for (Future<Collection<T>> future : futures) {
      long before = System.nanoTime();
      try {
        Collection<T> entities = future.get(Math.max(1L, timeLeft), TimeUnit.MILLISECONDS);
        globalResult.addAll(entities);
      } catch (Exception e) {
        future.cancel(true);
        failedRequests++;
        LOG.debug("Future execution error in {}.{}", serviceName, method.getName(), e);
      } finally {
        timeLeft -= TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
      }
    }
    if (failedRequests > 0) {
      LOG.warn(failedRequests + "/" + futures.size() + " " + serviceAgency + " agent(s) failed to respond to " + serviceName + "." + method.getName());
    }
    return globalResult;
  }

  private static <T> T deserializeAndRewriteAgentId(byte[] bytes, String agentId) throws IOException, ClassNotFoundException {
    if (bytes == null) {
      return null;
    }

    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
    try {
      Object deserialized = ois.readObject();
      rewriteAgentId(deserialized, agentId);
      return (T)deserialized;
    } finally {
      ois.close();
    }
  }

  private static void rewriteAgentId(Object obj, String agentId) {
    if (obj == null) {
      // do nothing
    } else if (obj instanceof Representable) {
      ((Representable)obj).setAgentId(agentId);
    } else if (obj instanceof Collection) {
      for (Object entity : (Collection<?>)obj) {
        if (entity instanceof Representable) {
          ((Representable)entity).setAgentId(agentId);
        }
      }
    } else {
      LOG.warn("Entity not of Representable type nor Collection of Representable types - cannot rewrite agent ID");
    }
  }

}
