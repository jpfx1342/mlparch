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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author John Petska
 */
public class XMLPatch {
	private final Document doc;
	private final XPathFactory xpfactory;
	
	public XMLPatch(String path) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		doc = builder.parse(path);
		xpfactory = XPathFactory.newInstance();
	}
	
	public NodeList getNodes(String query) throws XPathExpressionException {
		XPath xpath = xpfactory.newXPath();
		NodeList nodes = (NodeList) xpath.evaluate(query, doc, XPathConstants.NODESET);
		return nodes;
	}
	
	public void applyPatch(String query, XMLActor actor) throws XPathExpressionException {
		NodeList nodes = getNodes(query);
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			actor.process(node);
		}
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
	public static interface XMLActor {
		public void process(Node node);
	}
	public static class PrintValueXMLActor implements XMLActor {
		@Override public void process(Node node) {
			System.out.println(node.getNodeName()+"("+getNameFromType(node.getNodeType())+") = "+node.getNodeValue());
		}
	}
}
