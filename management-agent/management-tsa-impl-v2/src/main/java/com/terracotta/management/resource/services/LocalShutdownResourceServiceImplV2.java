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
import com.terracotta.management.service.ShutdownServiceV2;
import com.terracotta.management.service.TopologyServiceV2;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
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
@Path("/v2/local/shutdown")
public class LocalShutdownResourceServiceImplV2 {

  private static final Logger LOG = LoggerFactory.getLogger(LocalShutdownResourceServiceImplV2.class);

  private final ShutdownServiceV2 shutdownService;
  private final TopologyServiceV2 topologyService;

  public LocalShutdownResourceServiceImplV2() {
    this.shutdownService = ServiceLocator.locate(ShutdownServiceV2.class);
    this.topologyService = ServiceLocator.locate(TopologyServiceV2.class);
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public boolean shutdown(@Context UriInfo info, @FormParam("force") boolean force) {
    LOG.debug(String.format("Invoking BasicAuthShutdownResourceServiceImplV2.shutdown: %s", info.getRequestUri()));

    try {
      if (!force && !isPassiveStandbyAvailable()) {
        throw new ResourceRuntimeException("No passive server available in Standby mode. Use force option to stop the server.", Response.Status.BAD_REQUEST.getStatusCode());
      }

      shutdownService.shutdown(Collections.singleton(topologyService.getLocalServerName()));
    } catch (ServiceExecutionException see) {
      throw new ResourceRuntimeException("Failed to shutdown TSA", see, Response.Status.BAD_REQUEST.getStatusCode());
    }
    
    return true;
  }

  private boolean isPassiveStandbyAvailable() throws ServiceExecutionException {
    ServerGroupEntityV2 currentServerGroup = getCurrentServerGroup();
    for (ServerEntityV2 serverEntity : currentServerGroup.getServers()) {
      if (serverEntity.getAttributes().get("State").equals("PASSIVE_STANDBY")) {
        return true;
      }
    }
    return false;
  }

  private ServerGroupEntityV2 getCurrentServerGroup() throws ServiceExecutionException {
    String localServerName = topologyService.getLocalServerName();
    Collection<ServerGroupEntityV2> serverGroups = topologyService.getServerGroups(null);
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