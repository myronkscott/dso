/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.object.config.schema;

import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlInteger;
import org.apache.xmlbeans.XmlString;

import com.tc.config.schema.BaseNewConfigObject;
import com.tc.config.schema.context.ConfigContext;
import com.tc.config.schema.defaults.DefaultValueProvider;
import com.tc.config.schema.dynamic.IntConfigItem;
import com.tc.config.schema.dynamic.ParameterSubstituter;
import com.terracottatech.config.Client;
import com.terracottatech.config.DsoClientData;
import com.terracottatech.config.DsoClientDebugging;
import com.terracottatech.config.InstrumentationLogging;
import com.terracottatech.config.Modules;
import com.terracottatech.config.RuntimeLogging;
import com.terracottatech.config.RuntimeOutputOptions;
import com.terracottatech.config.TcConfigDocument.TcConfig;

import java.io.File;

public class NewL1DSOConfigObject extends BaseNewConfigObject implements NewL1DSOConfig {

  public static final String                     DSO_INSTRUMENTATION_LOGGING_OPTIONS_SUB_XPATH = "";

  private final IntConfigItem                    faultCount;

  private final DSOInstrumentationLoggingOptions instrumentationLoggingOptions;
  private final DSORuntimeLoggingOptions         runtimeLoggingOptions;
  private final DSORuntimeOutputOptions          runtimeOutputOptions;

  public NewL1DSOConfigObject(ConfigContext context) {
    super(context);

    this.context.ensureRepositoryProvides(DsoClientData.class);

    this.faultCount = this.context.intItem("fault-count");
    this.instrumentationLoggingOptions = new StandardDSOInstrumentationLoggingOptions(this.context);
    this.runtimeLoggingOptions = new StandardDSORuntimeLoggingOptions(this.context);
    this.runtimeOutputOptions = new StandardDSORuntimeOutputOptions(this.context);
  }

  public DSOInstrumentationLoggingOptions instrumentationLoggingOptions() {
    return this.instrumentationLoggingOptions;
  }

  public DSORuntimeLoggingOptions runtimeLoggingOptions() {
    return this.runtimeLoggingOptions;
  }

  public DSORuntimeOutputOptions runtimeOutputOptions() {
    return this.runtimeOutputOptions;
  }

  public IntConfigItem faultCount() {
    return faultCount;
  }

  public static void initializeClients(TcConfig config, DefaultValueProvider defaultValueProvider) throws XmlException {
    Client client;
    if (!config.isSetClients()) {
      client = config.addNewClients();
    } else {
      client = config.getClients();
    }
    initializeLogsDirectory(client, defaultValueProvider);
    initializeModules(client, defaultValueProvider);
    initiailizeDsoClient(client, defaultValueProvider);
  }

  private static void initializeLogsDirectory(Client client, DefaultValueProvider defaultValueProvider)
      throws XmlException {
    if (client != null && !client.isSetLogs()) {
      final XmlString defaultValue = (XmlString) defaultValueProvider.defaultFor(client.schemaType(), "logs");
      String substitutedString = ParameterSubstituter.substitute(defaultValue.getStringValue());

      client.setLogs(new File(substitutedString).getAbsolutePath());
    }
  }

  private static void initializeModules(Client client, DefaultValueProvider defaultValueProvider) {
    if (client != null && client.isSetModules()) {
      Modules modules = client.getModules();
      for (int i = 0; i < modules.sizeOfRepositoryArray(); i++) {
        String location = modules.getRepositoryArray(i);
        modules.setRepositoryArray(i, ParameterSubstituter.substitute(location));
      }
    }
  }

  private static void initiailizeDsoClient(Client client, DefaultValueProvider defaultValueProvider)
      throws XmlException {
    if (!client.isSetDso()) {
      DsoClientData dsoClientData = client.addNewDso();
      dsoClientData.setFaultCount(getDefaultFaultCount(client, defaultValueProvider));

      DsoClientDebugging debugging = dsoClientData.addNewDebugging();
      addDefaultInstrumentationLogging(client, debugging, defaultValueProvider);
      addDefaultRuntimeLogging(client, debugging, defaultValueProvider);
      addDefaultRuntimeOutputOptions(client, debugging, defaultValueProvider);
    } else {
      DsoClientData dsoClientData = client.getDso();
      if (!dsoClientData.isSetFaultCount()) {
        dsoClientData.setFaultCount(getDefaultFaultCount(client, defaultValueProvider));
      }

      if (!dsoClientData.isSetDebugging()) {
        DsoClientDebugging debugging = dsoClientData.addNewDebugging();
        addDefaultInstrumentationLogging(client, debugging, defaultValueProvider);
        addDefaultRuntimeLogging(client, debugging, defaultValueProvider);
      } else {
        DsoClientDebugging debugging = dsoClientData.getDebugging();
        if (!debugging.isSetInstrumentationLogging()) {
          addDefaultInstrumentationLogging(client, debugging, defaultValueProvider);
        } else {
          checkAndSetInstrumentationLogging(client, debugging.getInstrumentationLogging(), defaultValueProvider);
        }

        if (!debugging.isSetRuntimeLogging()) {
          addDefaultRuntimeLogging(client, debugging, defaultValueProvider);
        } else {
          checkAndSetRuntimeLogging(client, debugging.getRuntimeLogging(), defaultValueProvider);
        }

        if (!debugging.isSetRuntimeOutputOptions()) {
          addDefaultRuntimeOutputOptions(client, debugging, defaultValueProvider);
        } else {
          checkAndSetRuntimeOutputOptions(client, debugging.getRuntimeOutputOptions(), defaultValueProvider);
        }
      }
    }
  }

  private static int getDefaultFaultCount(Client client, DefaultValueProvider defaultValueProvider) throws XmlException {
    return ((XmlInteger) defaultValueProvider.defaultFor(client.schemaType(), "dso/fault-count")).getBigIntegerValue()
        .intValue();
  }

  private static void addDefaultInstrumentationLogging(Client client, DsoClientDebugging debugging,
                                                       DefaultValueProvider defaultValueProvider) throws XmlException {
    checkAndSetInstrumentationLogging(client, debugging.addNewInstrumentationLogging(), defaultValueProvider);
  }

  private static void addDefaultRuntimeLogging(Client client, DsoClientDebugging debugging,
                                               DefaultValueProvider defaultValueProvider) throws XmlException {
    checkAndSetRuntimeLogging(client, debugging.addNewRuntimeLogging(), defaultValueProvider);
  }

  private static void addDefaultRuntimeOutputOptions(Client client, DsoClientDebugging debugging,
                                                     DefaultValueProvider defaultValueProvider) throws XmlException {
    checkAndSetRuntimeOutputOptions(client, debugging.addNewRuntimeOutputOptions(), defaultValueProvider);

  }

  private static void checkAndSetInstrumentationLogging(Client client, InstrumentationLogging instrumentationLogging,
                                                        DefaultValueProvider defaultValueProvider) throws XmlException {
    if (!instrumentationLogging.isSetClass1()) {
      instrumentationLogging.setClass1(getDefaultClassInstrumentationLogging(client, defaultValueProvider));
    }

    if (!instrumentationLogging.isSetHierarchy()) {
      instrumentationLogging.setHierarchy(getDefaultHierarchyInstrumentationLogging(client, defaultValueProvider));
    }

    if (!instrumentationLogging.isSetLocks()) {
      instrumentationLogging.setLocks(getDefaultLocksInstrumentationLoggings(client, defaultValueProvider));
    }

    if (!instrumentationLogging.isSetTransientRoot()) {
      instrumentationLogging.setTransientRoot(getDefaultTransientRootInstrumentationLogging(client,
                                                                                            defaultValueProvider));
    }

    if (!instrumentationLogging.isSetRoots()) {
      instrumentationLogging.setRoots(getDefaultRootsInstrumentationLogging(client, defaultValueProvider));
    }

    if (!instrumentationLogging.isSetDistributedMethods()) {
      instrumentationLogging
          .setDistributedMethods(getDefaultDistributedMethodInstrumentationLogging(client, defaultValueProvider));
    }

  }

  private static void checkAndSetRuntimeLogging(Client client, RuntimeLogging runtimeLogging,
                                                DefaultValueProvider defaultValueProvider) throws XmlException {
    if (!runtimeLogging.isSetNonPortableDump()) {
      runtimeLogging.setNonPortableDump(getDefaultNonPortableDumpRuntimeLogging(client, defaultValueProvider));
    }

    if (!runtimeLogging.isSetLockDebug()) {
      runtimeLogging.setLockDebug(getDefaultLockDebugRuntimeLogging(client, defaultValueProvider));
    }

    if (!runtimeLogging.isSetFieldChangeDebug()) {
      runtimeLogging.setFieldChangeDebug(getDefaultFieldChangeDebugRuntimeLogging(client, defaultValueProvider));
    }

    if (!runtimeLogging.isSetWaitNotifyDebug()) {
      runtimeLogging.setWaitNotifyDebug(getDefaultWaitNotifyDebugRuntimeLogging(client, defaultValueProvider));
    }

    if (!runtimeLogging.isSetDistributedMethodDebug()) {
      runtimeLogging.setDistributedMethodDebug(getDefaultDistributedMethodDebugRuntimeLogging(client,
                                                                                              defaultValueProvider));
    }

    if (!runtimeLogging.isSetNewObjectDebug()) {
      runtimeLogging.setNewObjectDebug(getDefaultNewObjectDebugRuntimeLogging(client, defaultValueProvider));
    }

    if (!runtimeLogging.isSetNamedLoaderDebug()) {
      runtimeLogging.setNamedLoaderDebug(getDefaultNamedLoaderDebugRuntimeLogging(client, defaultValueProvider));
    }
  }

  private static void checkAndSetRuntimeOutputOptions(Client client, RuntimeOutputOptions runtimeOutputOptions,
                                                      DefaultValueProvider defaultValueProvider) throws XmlException {
    if (!runtimeOutputOptions.isSetAutoLockDetails()) {
      runtimeOutputOptions
          .setAutoLockDetails(getDefaultAutoLockDetailsRuntimeOutputOption(client, defaultValueProvider));
    }

    if (!runtimeOutputOptions.isSetCaller()) {
      runtimeOutputOptions.setCaller(getDefaultCallerRuntimeOutputOption(client, defaultValueProvider));
    }

    if (!runtimeOutputOptions.isSetFullStack()) {
      runtimeOutputOptions.setFullStack(getDefaultFullStackRuntimeOutputOption(client, defaultValueProvider));
    }
  }

  private static boolean getDefaultClassInstrumentationLogging(Client client, DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/instrumentation-logging/class"))
        .getBooleanValue();
  }

  private static boolean getDefaultHierarchyInstrumentationLogging(Client client,
                                                                   DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/instrumentation-logging/hierarchy"))
        .getBooleanValue();
  }

  private static boolean getDefaultLocksInstrumentationLoggings(Client client, DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/instrumentation-logging/locks"))
        .getBooleanValue();
  }

  private static boolean getDefaultTransientRootInstrumentationLogging(Client client,
                                                                       DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/instrumentation-logging/transient-root"))
        .getBooleanValue();
  }

  private static boolean getDefaultRootsInstrumentationLogging(Client client, DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/instrumentation-logging/roots"))
        .getBooleanValue();
  }

  private static boolean getDefaultDistributedMethodInstrumentationLogging(Client client,
                                                                           DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/instrumentation-logging/distributed-methods"))
        .getBooleanValue();
  }

  private static boolean getDefaultNonPortableDumpRuntimeLogging(Client client,
                                                                 DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-logging/non-portable-dump"))
        .getBooleanValue();
  }

  private static boolean getDefaultLockDebugRuntimeLogging(Client client, DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-logging/lock-debug")).getBooleanValue();
  }

  private static boolean getDefaultFieldChangeDebugRuntimeLogging(Client client,
                                                                  DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-logging/field-change-debug"))
        .getBooleanValue();
  }

  private static boolean getDefaultWaitNotifyDebugRuntimeLogging(Client client,
                                                                 DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-logging/wait-notify-debug"))
        .getBooleanValue();
  }

  private static boolean getDefaultDistributedMethodDebugRuntimeLogging(Client client,
                                                                        DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-logging/distributed-method-debug"))
        .getBooleanValue();
  }

  private static boolean getDefaultNewObjectDebugRuntimeLogging(Client client, DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-logging/new-object-debug"))
        .getBooleanValue();
  }

  private static boolean getDefaultNamedLoaderDebugRuntimeLogging(Client client,
                                                                  DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-logging/named-loader-debug"))
        .getBooleanValue();
  }

  private static boolean getDefaultAutoLockDetailsRuntimeOutputOption(Client client,
                                                                      DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-output-options/auto-lock-details"))
        .getBooleanValue();
  }

  private static boolean getDefaultCallerRuntimeOutputOption(Client client, DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-output-options/caller"))
        .getBooleanValue();
  }

  private static boolean getDefaultFullStackRuntimeOutputOption(Client client, DefaultValueProvider defaultValueProvider)
      throws XmlException {
    return ((XmlBoolean) defaultValueProvider.defaultFor(client.schemaType(),
                                                         "dso/debugging/runtime-output-options/full-stack"))
        .getBooleanValue();
  }

}
