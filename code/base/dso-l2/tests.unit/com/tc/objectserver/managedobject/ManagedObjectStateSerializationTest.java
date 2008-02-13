/*
 * All content copyright (c) 2003-2006 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.objectserver.managedobject;

import com.sleepycat.je.CursorConfig;
import com.tc.exception.ImplementMe;
import com.tc.logging.TCLogger;
import com.tc.logging.TCLogging;
import com.tc.object.ObjectID;
import com.tc.object.SerializationUtil;
import com.tc.object.dna.api.DNACursor;
import com.tc.object.dna.api.DNAEncoding;
import com.tc.object.dna.api.DNAWriter;
import com.tc.object.dna.api.LiteralAction;
import com.tc.object.dna.api.LogicalAction;
import com.tc.object.dna.api.PhysicalAction;
import com.tc.object.tx.TransactionID;
import com.tc.objectserver.api.NullObjectInstanceMonitor;
import com.tc.objectserver.core.api.ManagedObject;
import com.tc.objectserver.core.api.ManagedObjectState;
import com.tc.objectserver.core.api.TestDNA;
import com.tc.objectserver.persistence.api.PersistenceTransaction;
import com.tc.objectserver.persistence.impl.TestMutableSequence;
import com.tc.objectserver.persistence.impl.TestPersistenceTransactionProvider;
import com.tc.objectserver.persistence.sleepycat.DBEnvironment;
import com.tc.objectserver.persistence.sleepycat.ManagedObjectPersistorImpl;
import com.tc.objectserver.persistence.sleepycat.SleepycatCollectionFactory;
import com.tc.objectserver.persistence.sleepycat.SleepycatCollectionsPersistor;
import com.tc.objectserver.persistence.sleepycat.SleepycatPersistor;
import com.tc.objectserver.persistence.sleepycat.SleepycatSerializationAdapterFactory;
import com.tc.test.TCTestCase;
import com.tc.util.Assert;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ManagedObjectStateSerializationTest extends TCTestCase {
  private final TCLogger                     logger   = TCLogging.getTestingLogger(getClass());
  private final ObjectID                           objectID = new ObjectID(2000);

  private DBEnvironment                      env;
  private ManagedObjectPersistorImpl         managedObjectPersistor;
  private TestPersistenceTransactionProvider ptp;

  public void setUp() throws Exception {
    super.setUp();

    env = newDBEnvironment();
    SleepycatSerializationAdapterFactory sleepycatSerializationAdapterFactory = new SleepycatSerializationAdapterFactory();

    SleepycatPersistor persistor = new SleepycatPersistor(logger, env, sleepycatSerializationAdapterFactory);

    CursorConfig dbCursorConfig = new CursorConfig();
    ptp = new TestPersistenceTransactionProvider();
    CursorConfig rootDBCursorConfig = new CursorConfig();
    SleepycatCollectionFactory sleepycatCollectionFactory = new SleepycatCollectionFactory();
    SleepycatCollectionsPersistor sleepycatCollectionsPersistor = new SleepycatCollectionsPersistor(logger, env
        .getMapsDatabase(), sleepycatCollectionFactory);

    managedObjectPersistor = new ManagedObjectPersistorImpl(logger, env.getClassCatalogWrapper().getClassCatalog(),
                                                            sleepycatSerializationAdapterFactory, env
                                                                .getObjectDatabase(), env.getOidDatabase(),
                                                            dbCursorConfig, new TestMutableSequence(), env
                                                                .getRootDatabase(), rootDBCursorConfig, ptp,
                                                            sleepycatCollectionsPersistor, env.isParanoidMode());

    NullManagedObjectChangeListenerProvider listenerProvider = new NullManagedObjectChangeListenerProvider();
    ManagedObjectStateFactory.disableSingleton(true);
    ManagedObjectStateFactory.createInstance(listenerProvider, persistor);
  }

  private DBEnvironment newDBEnvironment() throws Exception {
    File dbHome = new File(this.getTempDirectory(), getClass().getName() + "db");
    dbHome.mkdirs();
    return new DBEnvironment(true, dbHome);
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    env.close();
    ManagedObjectStateFactory.disableSingleton(false);
  }

  public void testCheckIfMissingAnyManagedObjectType() throws Exception {
    Field[] fields = ManagedObjectState.class.getDeclaredFields();

    for (int i = 0; i < fields.length; i++) {
      Field field = fields[i];

      int fieldModifier = field.getModifiers();
      if (Modifier.isStatic(fieldModifier) && Modifier.isFinal(fieldModifier)) {
        Byte type = (Byte) field.get(null);
        switch (type.byteValue()) {
          case ManagedObjectState.PHYSICAL_TYPE:
            testPhysical();
            break;
          case ManagedObjectState.DATE_TYPE:
            testDate();
            break;
          case ManagedObjectState.MAP_TYPE:
          case ManagedObjectState.PARTIAL_MAP_TYPE:
            // Map type is tested in another test.
            break;
          case ManagedObjectState.LINKED_HASHMAP_TYPE:
            testLinkedHashMap();
            break;
          case ManagedObjectState.ARRAY_TYPE:
            testArray();
            break;
          case ManagedObjectState.LITERAL_TYPE:
            testLiteral();
            break;
          case ManagedObjectState.LIST_TYPE:
            testList();
            break;
          case ManagedObjectState.SET_TYPE:
            testSet();
            break;
          case ManagedObjectState.TREE_SET_TYPE:
            testTreeSet();
            break;
          case ManagedObjectState.TREE_MAP_TYPE:
            testTreeMap();
            break;
          case ManagedObjectState.QUEUE_TYPE:
            testLinkedBlockingQueue();
            break;
          case ManagedObjectState.CONCURRENT_HASHMAP_TYPE:
            testConcurrentHashMap();
            break;
          case ManagedObjectState.URL_TYPE:
            testURL();
            break;
          default:
            throw new AssertionError("Type " + type
                                     + " does not have a test case in ManagedObjectStateSerializationTest.");
        }
      }
    }
  }

  public void testPhysical() throws Exception {
    String className = "com.tc.objectserver.managedobject.ManagedObjectStateSerializationTest";
    TestDNACursor cursor = new TestDNACursor();

    cursor.addPhysicalAction("field1", new ObjectID(2002), true);
    cursor.addPhysicalAction("field2", new ObjectID(2003), true);
    cursor.addPhysicalAction("field3", new Integer(33), false);

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.PHYSICAL_TYPE);
  }

  public void testDate() throws Exception {
    String className = "java.util.Date";

    TestDNACursor cursor = new TestDNACursor();

    cursor.addLogicalAction(SerializationUtil.SET_TIME, new Long[] { new Long(System.currentTimeMillis()) });
    cursor.addLogicalAction(SerializationUtil.SET_NANOS, new Integer[] { new Integer(0) });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.DATE_TYPE);
  }

  public void testLinkedHashMap() throws Exception {
    String className = "java.util.LinkedHashMap";
    String ACCESS_ORDER_FIELDNAME = "java.util.LinkedHashMap.accessOrder";

    TestDNACursor cursor = new TestDNACursor();

    cursor.addPhysicalAction(ACCESS_ORDER_FIELDNAME, Boolean.FALSE, false);

    cursor.addLogicalAction(SerializationUtil.PUT, new Object[] { new ObjectID(2002), new ObjectID(2003) });
    cursor.addLogicalAction(SerializationUtil.PUT, new Object[] { new ObjectID(2004), new ObjectID(2005) });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.LINKED_HASHMAP_TYPE);
  }

  public void testArray() throws Exception {
    String className = "[java.lang.Integer";
    TestDNACursor cursor = new TestDNACursor();

    cursor.addArrayAction(new Integer[] { new Integer(27) });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.ARRAY_TYPE);
  }

  public void testLiteral() throws Exception {
    String className = "java.lang.Integer";
    TestDNACursor cursor = new TestDNACursor();

    cursor.addLiteralAction(new Integer(27));

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.LITERAL_TYPE);
  }

  public void testList() throws Exception {
    String className = "java.util.ArrayList";
    TestDNACursor cursor = new TestDNACursor();

    cursor.addLogicalAction(SerializationUtil.ADD, new Object[] { new ObjectID(2002) });
    cursor.addLogicalAction(SerializationUtil.ADD, new Object[] { new ObjectID(2003) });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.LIST_TYPE);
  }

  public void testSet() throws Exception {
    String className = "java.util.HashSet";
    TestDNACursor cursor = new TestDNACursor();

    cursor.addLogicalAction(SerializationUtil.ADD, new Object[] { new ObjectID(2002) });
    cursor.addLogicalAction(SerializationUtil.ADD, new Object[] { new ObjectID(2003) });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.SET_TYPE);
  }

  public void testTreeSet() throws Exception {
    String className = "java.util.TreeSet";
    String COMPARATOR_FIELDNAME = "java.util.TreeMap.comparator";

    TestDNACursor cursor = new TestDNACursor();

    cursor.addPhysicalAction(COMPARATOR_FIELDNAME, new ObjectID(2001), true);

    cursor.addLogicalAction(SerializationUtil.ADD, new Object[] { new ObjectID(2002) });
    cursor.addLogicalAction(SerializationUtil.ADD, new Object[] { new ObjectID(2003) });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.TREE_SET_TYPE);
  }

  public void testTreeMap() throws Exception {
    String className = "java.util.TreeMap";
    String COMPARATOR_FIELDNAME = "java.util.TreeMap.comparator";

    TestDNACursor cursor = new TestDNACursor();

    cursor.addPhysicalAction(COMPARATOR_FIELDNAME, new ObjectID(2001), true);

    cursor.addLogicalAction(SerializationUtil.PUT, new Object[] { new ObjectID(2002), new ObjectID(2003) });
    cursor.addLogicalAction(SerializationUtil.PUT, new Object[] { new ObjectID(2004), new ObjectID(2005) });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.TREE_MAP_TYPE);
  }

  public void testLinkedBlockingQueue() throws Exception {
    String className = "java.util.concurrent.LinkedBlockingQueue";
    String TAKE_LOCK_FIELD_NAME = "java.util.concurrent.LinkedBlockingQueue.takeLock";
    String PUT_LOCK_FIELD_NAME = "java.util.concurrent.LinkedBlockingQueue.putLock";
    String CAPACITY_FIELD_NAME = "java.util.concurrent.LinkedBlockingQueue.capacity";

    TestDNACursor cursor = new TestDNACursor();

    cursor.addPhysicalAction(TAKE_LOCK_FIELD_NAME, new ObjectID(2001), true);
    cursor.addPhysicalAction(PUT_LOCK_FIELD_NAME, new ObjectID(2002), true);
    cursor.addPhysicalAction(CAPACITY_FIELD_NAME, new Integer(100), false);

    cursor.addLogicalAction(SerializationUtil.PUT, new Object[] { new ObjectID(2003) });
    cursor.addLogicalAction(SerializationUtil.PUT, new Object[] { new ObjectID(2004) });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.QUEUE_TYPE);
  }

  public void testConcurrentHashMap() throws Exception {
    String className = "java.util.concurrent.ConcurrentHashMap";
    String SEGMENT_MASK_FIELD_NAME = className + ".segmentMask";
    String SEGMENT_SHIFT_FIELD_NAME = className + ".segmentShift";
    String SEGMENT_FIELD_NAME = className + ".segments";

    TestDNACursor cursor = new TestDNACursor();

    cursor.addPhysicalAction(SEGMENT_MASK_FIELD_NAME, new Integer(10), false);
    cursor.addPhysicalAction(SEGMENT_SHIFT_FIELD_NAME, new Integer(20), false);
    cursor.addLiteralAction(new Integer(2));
    cursor.addPhysicalAction(SEGMENT_FIELD_NAME + 0, new ObjectID(2001), true);
    cursor.addPhysicalAction(SEGMENT_FIELD_NAME + 1, new ObjectID(2002), true);

    cursor.addLogicalAction(SerializationUtil.PUT, new Object[] { new ObjectID(2002), new ObjectID(2003) });
    cursor.addLogicalAction(SerializationUtil.PUT, new Object[] { new ObjectID(2004), new ObjectID(2005) });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.CONCURRENT_HASHMAP_TYPE);
  }

  public void testURL() throws Exception {
    String className = "java.net.URL";

    TestDNACursor cursor = new TestDNACursor();

    cursor.addLogicalAction(SerializationUtil.URL_SET, new Object[] { "http", "terracotta.org", new Integer(8080),
        "auth", "user:pass", "/test", "par1=val1", "ref" });

    ManagedObjectState state = applyValidation(className, cursor);

    serializationValidation(state, cursor, ManagedObjectState.URL_TYPE);
  }

  protected ManagedObjectState applyValidation(String className, DNACursor dnaCursor) throws Exception {
    ManagedObject mo = new ManagedObjectImpl(objectID);

    TestDNA dna = new TestDNA(dnaCursor);
    dna.typeName = className;
    mo.apply(dna, new TransactionID(1), new BackReferences(), new NullObjectInstanceMonitor(), false);

    PersistenceTransaction txn = ptp.newTransaction();
    managedObjectPersistor.saveObject(txn, mo);
    txn.commit();

    ManagedObjectState state = mo.getManagedObjectState();
    TestDNAWriter dnaWriter = dehydrate(state);
    validate(dnaCursor, dnaWriter);

    return state;
  }

  protected void serializationValidation(ManagedObjectState state, DNACursor dnaCursor, byte type) throws Exception {
    ManagedObject loaded = managedObjectPersistor.loadObjectByID(objectID);
    TestDNAWriter dnaWriter = dehydrate(loaded.getManagedObjectState());
    validate(dnaCursor, dnaWriter);
  }

  private TestDNAWriter dehydrate(ManagedObjectState state) throws Exception {
    TestDNAWriter dnaWriter = new TestDNAWriter();
    state.dehydrate(objectID, dnaWriter);
    return dnaWriter;
  }

  private void validate(DNACursor dnaCursor, TestDNAWriter writer) throws Exception {
    Assert.assertEquals(dnaCursor.getActionCount(), writer.getActionCount());
    dnaCursor.reset();
    while (dnaCursor.next()) {
      Object action = dnaCursor.getAction();
      Assert.assertTrue(writer.containsAction(action));
    }
  }

  public class TestDNAWriter implements DNAWriter {
    private List physicalActions = new ArrayList();
    private List logicalActions  = new ArrayList();
    private List literalActions  = new ArrayList();

    public TestDNAWriter() {
      //
    }

    public void addLogicalAction(int method, Object[] parameters) {
      logicalActions.add(new LogicalAction(method, parameters));
    }

    public void addPhysicalAction(String field, Object value) {
      addPhysicalAction(field, value, value instanceof ObjectID);
    }

    public void finalizeDNA(boolean isDeltaDNA) {
      //
    }

    public void addArrayElementAction(int index, Object value) {
      //
    }

    public void addEntireArray(Object value) {
      physicalActions.add(new PhysicalAction(value));
    }

    public void addLiteralValue(Object value) {
      literalActions.add(new LiteralAction(value));
    }

    public void setParentObjectID(ObjectID id) {
      //
    }

    public void setArrayLength(int length) {
      //
    }

    public void addPhysicalAction(String fieldName, Object value, boolean canBeReference) {
      physicalActions.add(new PhysicalAction(fieldName, value, canBeReference));
    }

    public int getActionCount() {
      return logicalActions.size() + physicalActions.size() + literalActions.size();
    }

    private boolean containsAction(Object targetAction) {
      if (targetAction instanceof LogicalAction) {
        return containsLogicalAction((LogicalAction) targetAction);
      } else if (targetAction instanceof PhysicalAction) {
        return containsPhysicalAction((PhysicalAction) targetAction);
      } else if (targetAction instanceof LiteralAction) { return containsLiteralAction((LiteralAction) targetAction); }

      return false;
    }

    private boolean containsLogicalAction(LogicalAction targetAction) {
      for (Iterator i = logicalActions.iterator(); i.hasNext();) {
        LogicalAction action = (LogicalAction) i.next();
        if (identicalLogicalAction(targetAction, action)) { return true; }
      }
      return false;
    }

    private boolean containsPhysicalAction(PhysicalAction targetAction) {
      for (Iterator i = physicalActions.iterator(); i.hasNext();) {
        PhysicalAction action = (PhysicalAction) i.next();
        if (identicalPhysicalAction(targetAction, action)) { return true; }
      }
      return false;
    }

    private boolean containsLiteralAction(LiteralAction targetAction) {
      for (Iterator i = literalActions.iterator(); i.hasNext();) {
        LiteralAction action = (LiteralAction) i.next();
        if (identicalLiteralAction(targetAction, action)) { return true; }
      }
      return false;
    }

    private boolean identicalLiteralAction(LiteralAction a1, LiteralAction a2) {
      if (a1 == null || a2 == null) { return false; }
      if (a1.getObject() == null || a2.getObject() == null) { return false; }

      return a1.getObject().equals(a2.getObject());
    }

    private boolean identicalPhysicalAction(PhysicalAction a1, PhysicalAction a2) {
      if (a1 == null || a2 == null) { return false; }

      if (!a1.isEntireArray() && !a2.isEntireArray()) {
        if (a1.getFieldName() == null || a2.getFieldName() == null) { return false; }
      }

      if (a1.isEntireArray() != a2.isEntireArray()) { return false; }

      if (a1.getObject() == null && a2.getObject() == null) { return true; }
      if (a1.getObject() == null && a2.getObject() != null) { return false; }
      if (a1.getObject() != null && a2.getObject() == null) { return false; }

      if (a1.isEntireArray()) {
        return Arrays.equals((Object[]) a1.getObject(), (Object[]) a2.getObject());
      } else if (a1.getObject() instanceof Object[] && a2.getObject() instanceof Object[]) {
        return Arrays.equals((Object[]) a1.getObject(), (Object[]) a2.getObject());
      } else {
        if (a1.getFieldName().equals(a2.getFieldName())) { return (a1.getObject().equals(a2.getObject())); }
      }
      return false;
    }

    private boolean identicalLogicalAction(LogicalAction a1, LogicalAction a2) {
      if (a1 == null || a2 == null) { return false; }
      if (a1.getParameters() == null || a2.getParameters() == null) { return false; }

      if (a1.getMethod() == a2.getMethod()) {
        if (a1.getParameters().length == a2.getParameters().length) {
          for (int i = 0; i < a1.getParameters().length; i++) {
            if (!a1.getParameters()[i].equals(a2.getParameters()[i])) { return false; }
          }
          return true;
        }
      }
      return false;
    }

    public void addClassLoaderAction(String classLoaderFieldName, Object value) {
      //

    }

    public void addSubArrayAction(int start, Object array, int length) {
      //
    }
  }

  public static class TestDNACursor implements DNACursor {
    private List actions = new ArrayList();
    private int  current = -1;

    public void addPhysicalAction(String addFieldName, Object addObj, boolean isref) {
      actions.add(new PhysicalAction(addFieldName, addObj, isref));
    }

    public void addLogicalAction(int method, Object params[]) {
      actions.add(new LogicalAction(method, params));
    }

    public void addArrayAction(Object[] objects) {
      actions.add(new PhysicalAction(objects));
    }

    public void addLiteralAction(Object value) {
      actions.add(new LiteralAction(value));
    }

    public boolean next() {
      return actions.size() > ++current;
    }

    public LogicalAction getLogicalAction() {
      return (LogicalAction) actions.get(current);
    }

    public Object getAction() {
      return actions.get(current);
    }

    public PhysicalAction getPhysicalAction() {
      return (PhysicalAction) actions.get(current);
    }

    public boolean next(DNAEncoding encoding) {
      throw new ImplementMe();
    }

    public int getActionCount() {
      return actions.size();
    }

    public void reset() throws UnsupportedOperationException {
      current = -1;
    }
  }

  public interface MyProxyInf1 {
    public int getValue();

    public void setValue(int i);
  }

  public interface MyProxyInf2 {
    public String getStringValue();

    public void setStringValue(String str);
  }

  public static class MyInvocationHandler implements InvocationHandler {
    private Map values       = new HashMap();
    private Map stringValues = new HashMap();

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("getValue")) {
        return values.get(proxy);
      } else if (method.getName().equals("setValue")) {
        values.put(proxy, args[0]);
        return null;
      } else if (method.getName().equals("setStringValue")) {
        stringValues.put(proxy, args[0]);
        return null;
      } else if (method.getName().equals("getStringValue")) {
        return stringValues.get(proxy);
      } else if (method.getName().equals("hashCode")) { return new Integer(System.identityHashCode(proxy)); }
      return null;
    }
  }
}
