/*
 * All content copyright (c) 2003-2006 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tc.config.schema.setup;

import org.apache.xmlbeans.QNameSet;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlDocumentProperties;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.xml.stream.XMLInputStream;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.ext.LexicalHandler;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

/**
 * An {@link XmlObject} used to represent beans in tests.
 */
public class TestXmlObject implements XmlObject {

  private final Object value;
  private final Map    childValues;

  public TestXmlObject(Object value) {
    this.value = value;
    this.childValues = new HashMap();
  }

  public TestXmlObject() {
    this(null);
  }

  public String getStringValue() {
    return (String) value;
  }

  public BigInteger getBigIntegerValue() {
    return new BigInteger(((Integer) value).toString());
  }
  
  public boolean getBooleanValue() {
    return ((Boolean) value).booleanValue();
  }
  
  public String[] getTheStringArray() {
    return (String[]) value;
  }

  public void setChildBean(String xpath, XmlObject[] values) {
    this.childValues.put(xpath, values);
  }

  public void setChildBean(String xpath, XmlObject value) {
    setChildBean(xpath, new XmlObject[] { value });
  }

  public SchemaType schemaType() {
    return null;
  }

  public boolean validate() {
    return true;
  }

  public boolean validate(XmlOptions arg0) {
    return true;
  }

  public XmlObject[] selectPath(String arg0) {
    return (XmlObject[]) this.childValues.get(arg0);
  }

  public XmlObject[] selectPath(String arg0, XmlOptions arg1) {
    return (XmlObject[]) this.childValues.get(arg0);
  }

  public XmlObject[] execQuery(String arg0) {
    return null;
  }

  public XmlObject[] execQuery(String arg0, XmlOptions arg1) {
    return null;
  }

  public XmlObject changeType(SchemaType arg0) {
    return null;
  }

  public XmlObject substitute(QName arg0, SchemaType arg1) {
    return null;
  }

  public boolean isNil() {
    return false;
  }

  public void setNil() { /* nothing here */
  }

  public boolean isImmutable() {
    return false;
  }

  public XmlObject set(XmlObject arg0) {
    return null;
  }

  public XmlObject copy() {
    return null;
  }

  public boolean valueEquals(XmlObject arg0) {
    return false;
  }

  public int valueHashCode() {
    return 0;
  }

  public int compareTo(Object arg0) {
    return 0;
  }

  public int compareValue(XmlObject arg0) {
    return 0;
  }

  public XmlObject[] selectChildren(QName arg0) {
    return null;
  }

  public XmlObject[] selectChildren(String arg0, String arg1) {
    return null;
  }

  public XmlObject[] selectChildren(QNameSet arg0) {
    return null;
  }

  public XmlObject selectAttribute(QName arg0) {
    return null;
  }

  public XmlObject selectAttribute(String arg0, String arg1) {
    return null;
  }

  public XmlObject[] selectAttributes(QNameSet arg0) {
    return null;
  }

  public Object monitor() {
    return null;
  }

  public XmlDocumentProperties documentProperties() {
    return null;
  }

  public XmlCursor newCursor() {
    return null;
  }

  public XMLInputStream newXMLInputStream() {
    return null;
  }

  public XMLStreamReader newXMLStreamReader() {
    return null;
  }

  public String xmlText() {
    return null;
  }

  public InputStream newInputStream() {
    return null;
  }

  public Reader newReader() {
    return null;
  }

  public Node newDomNode() {
    return null;
  }

  public Node getDomNode() {
    return null;
  }

  public void save(ContentHandler arg0, LexicalHandler arg1) { /* nothing here */
  }

  public void save(File arg0) { /* nothing here */
  }

  public void save(OutputStream arg0) { /* nothing here */
  }

  public void save(Writer arg0) { /* nothing here */
  }

  public XMLInputStream newXMLInputStream(XmlOptions arg0) {
    return null;
  }

  public XMLStreamReader newXMLStreamReader(XmlOptions arg0) {
    return null;
  }

  public String xmlText(XmlOptions arg0) {
    return null;
  }

  public InputStream newInputStream(XmlOptions arg0) {
    return null;
  }

  public Reader newReader(XmlOptions arg0) {
    return null;
  }

  public Node newDomNode(XmlOptions arg0) {
    return null;
  }

  public void save(ContentHandler arg0, LexicalHandler arg1, XmlOptions arg2) { /* nothing here */
  }

  public void save(File arg0, XmlOptions arg1) { /* nothing here */
  }

  public void save(OutputStream arg0, XmlOptions arg1) { /* nothing here */
  }

  public void save(Writer arg0, XmlOptions arg1) { /* nothing here */
  }

  public void dump() { /* nothing here */
  }

}
