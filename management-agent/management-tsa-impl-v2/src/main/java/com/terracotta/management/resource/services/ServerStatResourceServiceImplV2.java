/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.terracotta.management.resource.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.management.ServiceExecutionException;
import org.terracotta.management.ServiceLocator;
import org.terracotta.management.resource.exceptions.ResourceRuntimeException;

import com.terracotta.management.resource.ServerEntityV2;
import com.terracotta.management.resource.ServerGroupEntityV2;
import com.terracotta.management.service.TopologyServiceV2;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * A resource service for performing local server shutdown.
 * 
 * @author Ludovic Orban
 */
@Path("/v2/local/stat")
public class ServerStatResourceServiceImplV2 {

  private static final Logger LOG = LoggerFactory.getLogger(ServerStatResourceServiceImplV2.class);

  private final TopologyServiceV2 topologyService;

  public ServerStatResourceServiceImplV2() {
    this.topologyService = ServiceLocator.locate(TopologyServiceV2.class);
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String shutdown(@Context UriInfo info) {
    LOG.debug(String.format("Invoking ServerStatResourceServiceImplV2.shutdown: %s", info.getRequestUri()));

    try {
      ServerGroupEntityV2 currentServerGroup = getCurrentServerGroup();
      ServerEntityV2 currentServer = getCurrentServer(currentServerGroup);

      String health = "OK";
      String role = (currentServer.getAttributes().get("State").equals("ACTIVE-COORDINATOR") ? "ACTIVE" : "PASSIVE");
      Object state = currentServer.getAttributes().get("State");
      Object managementPort = currentServer.getAttributes().get("ManagementPort");
      String serverGroupName = currentServerGroup.getName();

      return
          "health: " + health + "\n" +
          "role: " + role + "\n" +
          "state: " + state + "\n" +
          "managementport: " + managementPort + "\n" +
          "group name: " + serverGroupName + "\n";
    } catch (ServiceExecutionException see) {
      throw new ResourceRuntimeException("Failed to shutdown TSA", see, Response.Status.BAD_REQUEST.getStatusCode());
    }
  }

  private ServerEntityV2 getCurrentServer(ServerGroupEntityV2 currentServerGroup) throws ServiceExecutionException {
    String localServerName = topologyService.getLocalServerName();
    for (ServerEntityV2 server : currentServerGroup.getServers()) {
      if (server.getAttributes().get("Name").equals(localServerName)) {
        return server;
      }
    }
    return null;
  }

  private ServerGroupEntityV2 getCurrentServerGroup() throws ServiceExecutionException {
    String localServerName = topologyService.getLocalServerName();
    Collection<ServerGroupEntityV2> serverGroups = topologyService.getServerGroups(Collections.singleton(localServerName));
    for (ServerGroupEntityV2 serverGroup : serverGroups) {
      Set<ServerEntityV2> servers = serverGroup.getServers();
      for (ServerEntityV2 server : servers) {
        if (server.getAttributes().get("Name").equals(localServerName)) {
          return serverGroup;
        }
      }
    }
    return null;
  }

}