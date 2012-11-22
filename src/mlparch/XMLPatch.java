/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author John Petska
 */
public class XMLPatch {
	public final Document doc;
	private final XPathFactory xpfactory;
	
	public XMLPatch(String path) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		doc = builder.parse(path);
		xpfactory = XPathFactory.newInstance();
	}
	public XMLPatch(Document doc) throws Exception {
		this.doc = doc;
		xpfactory = XPathFactory.newInstance();
	}
	public NodeList getNodes(String query) throws XPathExpressionException {
		XPath xpath = xpfactory.newXPath();
		NodeList nodes = (NodeList) xpath.evaluate(query, doc, XPathConstants.NODESET);
		return nodes;
	}
	public void applyPatch(NodeList nodes, NamedNodeMap config, XMLPatchOp op) throws XPathExpressionException {
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			op.apply(config, node);
		}
	}
	public void applyPatch(String query, NamedNodeMap config, XMLPatchOp op) throws XPathExpressionException {
		applyPatch(getNodes(query), config, op);
	}
	public static String getNameFromType(short type) {
		switch (type) {
			case Node.ELEMENT_NODE:                return "ELEMENT_NODE";
			case Node.ATTRIBUTE_NODE:              return "ATTRIBUTE_NODE";
			case Node.TEXT_NODE:                   return "TEXT_NODE";
			case Node.CDATA_SECTION_NODE:          return "CDATA_SECTION_NODE";
			case Node.ENTITY_REFERENCE_NODE:       return "ENTITY_REFERENCE_NODE";
			case Node.ENTITY_NODE:                 return "ENTITY_NODE";
			case Node.PROCESSING_INSTRUCTION_NODE: return "PROCESSING_INSTRUCTION_NODE";
			case Node.COMMENT_NODE:                return "COMMENT_NODE";
			case Node.DOCUMENT_NODE:               return "DOCUMENT_NODE";
			case Node.DOCUMENT_TYPE_NODE:          return "DOCUMENT_TYPE_NODE";
			case Node.DOCUMENT_FRAGMENT_NODE:      return "DOCUMENT_FRAGMENT_NODE";
			case Node.NOTATION_NODE:               return "NOTATION_NODE";
		}
		return "UNKNOWN_NODE";
	}
	public static interface XMLPatchOp {
		public void apply(NamedNodeMap config, Node target);
	}
	public static class PrintValueXMLActor implements XMLPatchOp {
		@Override public void apply(NamedNodeMap config, Node target) {
			System.out.println(target.getNodeName()+"("+getNameFromType(target.getNodeType())+") = "+target.getNodeValue());
		}
	}
	public static class PrintXMLActor implements XMLPatchOp {
		@Override public void apply(NamedNodeMap config, Node target) {
			System.out.println(target.toString());
		}
	}
}
