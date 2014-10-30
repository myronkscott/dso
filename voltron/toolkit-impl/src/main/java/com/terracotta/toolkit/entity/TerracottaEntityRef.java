package com.terracotta.toolkit.entity;

import org.terracotta.connection.entity.ConfigurationMismatchException;
import org.terracotta.connection.entity.Entity;
import org.terracotta.connection.entity.EntityConfiguration;
import org.terracotta.connection.entity.EntityMaintenanceRef;
import org.terracotta.connection.entity.EntityRef;
import org.terracotta.entity.EntityClientServiceFactory;

import com.tc.net.GroupID;
import com.tc.platform.PlatformService;
import com.terracotta.toolkit.concurrent.locks.ToolkitLockingApi;
import com.terracotta.toolkit.concurrent.locks.UnnamedToolkitLock;

/**
 * @author twu
 */
public class TerracottaEntityRef<T extends Entity> implements EntityMaintenanceRef<T>, EntityRef<T> {
  private final PlatformService platformService;
  private final MaintenanceModeService maintenanceModeService;
  private final Class<T> type;
  private final String name;
  private final UnnamedToolkitLock createLock;

  private enum ReferenceState {
    FREE, IN_USE, MAINTENANCE
  }

  private T entity;
  private ReferenceState state = ReferenceState.FREE;

  public TerracottaEntityRef(final PlatformService platformService, final MaintenanceModeService maintenanceModeService, final Class<T> type, final String name) {
    this.platformService = platformService;
    this.maintenanceModeService = maintenanceModeService;
    this.type = type;
    this.name = name;
    createLock = ToolkitLockingApi.createConcurrentTransactionLock("foo", platformService);
  }

  @Override
  public synchronized T acquireEntity() {
    if (state == ReferenceState.IN_USE) {
      return entity;
    } else if (state == ReferenceState.FREE) {
      maintenanceModeService.readLockEntity(type, name);
      EntityClientEndpointImpl endpoint = (EntityClientEndpointImpl) platformService.lookupRoot(name, new GroupID(0));
      if (endpoint == null) {
        maintenanceModeService.readUnlockEntity(type, name);
        throw new IllegalStateException("doesn't exist");
      }
      entity = EntityClientServiceFactory.creationServiceForType(type).create(endpoint, endpoint.getEntityConfiguration());
      state = ReferenceState.IN_USE;
    }
    return entity;
  }

  @Override
  public T getEntity() {
    throw new UnsupportedOperationException("Implement me!");
  }

  @Override
  public T acquireEntity(final EntityConfiguration configuration) throws ConfigurationMismatchException {
    throw new UnsupportedOperationException("Implement me!");
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public synchronized void destroy() {
    checkMaintenanceMode();
    // success! you can't create, so obviously destruction works by default.
  }

  @Override
  public synchronized void create(final EntityConfiguration configuration) {
    checkMaintenanceMode();

    EntityClientEndpointImpl endpoint = (EntityClientEndpointImpl) platformService.lookupRoot(name, new GroupID(0));
    if (endpoint == null) {
      createLock.lock();
      try {
        endpoint = new EntityClientEndpointImpl(type.getName(), configuration);
        platformService.lookupOrCreateRoot(name, endpoint, new GroupID(0));
      } finally {
        createLock.unlock(); // TODO: This should probably be synchronous in some way
      }
    } else {
      throw new IllegalStateException("Already exists");
    }
  }

  @Override
  public synchronized void exitMaintenanceMode() {
    checkMaintenanceMode();
    maintenanceModeService.exitMaintenanceMode(type, name);
    state = ReferenceState.FREE;
  }

  public synchronized EntityMaintenanceRef<T> enterMaintenanceMode() {
    if (state != ReferenceState.FREE) {
      throw new IllegalStateException("Reference is not free to enter maintenance mode.");
    }
    maintenanceModeService.enterMaintenanceMode(type, name);
    state = ReferenceState.MAINTENANCE;
    return this;
  }

  private void checkMaintenanceMode() {
    if (state != ReferenceState.MAINTENANCE) {
      throw new IllegalStateException("Not in maintenance mode");
    }
  }
}
