/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
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
	private final DocumentBuilder docBuilder;
	private final XPathFactory xpfactory;
	
	public static class XMLPDoc {
		public boolean dummied;
		public Document doc;

		public XMLPDoc(boolean dummied, Document doc) {
			this.dummied = dummied;
			this.doc = doc;
		}
	}
	public final HashMap<String, XMLPDoc> docMap = new HashMap<String, XMLPDoc>();
	
	public final HashMap<String, XMLPatchOp> opList = new HashMap<String, XMLPatchOp>();
	public void addDefaultOps() {
		opList.put("print", new XMLPatchOpPrint());
		opList.put("=", new XMLPatchOpSet());
		opList.put("+=", new XMLPatchOpAddSet());
		opList.put("-=", new XMLPatchOpSubSet());
		opList.put("*=", new XMLPatchOpMulSet());
		opList.put("/=", new XMLPatchOpDivSet());
		opList.put("floor", new XMLPatchOpFloor());
		opList.put("ceil", new XMLPatchOpCeil());
		opList.put("round", new XMLPatchOpRound());
		opList.put("sqrt", new XMLPatchOpSqrt());
	}
	
	public static class DummyInputStream extends SequenceInputStream {
		static byte[] open = "<dummy>".getBytes();
		static byte[] close = "</dummy>".getBytes();
		
		public DummyInputStream(InputStream is) {
			super(Collections.enumeration(Arrays.asList(new InputStream[] {
				new ByteArrayInputStream(open),
				is,
				new ByteArrayInputStream(close),
			})));
		}
	}
	
	public static class DummyOutputStream extends OutputStream {
		private int state = 0;
		//state = 0, searching for opening dummy. data is discarded
		//state = 1, searching for closing dummy, data is written
		//state = 2, closing dummy matched, data is discarded (never leaves this state)
		byte[] buffer = new byte[16];
		int bpos = 0;

		OutputStream os;
		
		public DummyOutputStream(OutputStream os) {
			this.os = os;
		}
		
		@Override
		public void write(int b) throws IOException {
			byte by = (byte)(0xFF&b);
			switch (state) {
				case 0: //search for opening dummy, discarding all data until a match
					if (by == DummyInputStream.open[bpos]) {
						buffer[bpos++] = by;
						if (bpos == DummyInputStream.open.length) {
							state = 1;
							bpos = 0;
						}
					} else {
						bpos = 0;
					}
					break;
				case 1: //search for closing dummy, writing any data that doesn't match
					if (by == DummyInputStream.close[bpos]) {
						buffer[bpos++] = by;
						if (bpos == DummyInputStream.close.length) {
							state = 2;
							bpos = 0;
						}
					} else {
						os.write(buffer, 0, bpos);
						os.write(b);
						bpos = 0;
					}
					break;
				case 2: //done, discard all data
					break;
			}
		}

		@Override
		public void flush() throws IOException {
			os.flush();
		}

		@Override
		public void close() throws IOException {
			os.close();
		}
	}
	public XMLPatch() throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		docBuilder = factory.newDocumentBuilder();
		xpfactory = XPathFactory.newInstance();
		addDefaultOps();
	}
	public Document getDoc(File root, String target, boolean dummied) throws FileNotFoundException, SAXException, IOException {
		XMLPDoc doc = docMap.get(target);
		if (doc == null) {
			File file = new File(root==null?new File("."):root, target);
			if (!file.exists() || !file.isFile())
				throw new RuntimeException("Couldn't locate target! (\""+file.getPath()+"\")");
			
			if (dummied) {
				doc = new XMLPDoc(dummied, docBuilder.parse(new DummyInputStream(new FileInputStream(file))));
			} else {
				doc = new XMLPDoc(dummied, docBuilder.parse(new FileInputStream(file)));
			}
			docMap.put(target, doc);
		}
		return doc.doc;
	}
	public NodeList getNodes(Document doc, String query) throws XPathExpressionException {
		XPath xpath = xpfactory.newXPath();
		NodeList nodes = (NodeList) xpath.evaluate(query, doc, XPathConstants.NODESET);
		return nodes;
	}
	public void applyOp(Document doc, NodeList nodes, Node config, XMLPatchOp op) throws XPathExpressionException {
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			try {
				op.apply(config, node);
			} catch (Throwable t) {
				System.err.println("Exception applying op on "+node+": "+t.getLocalizedMessage());
			}
		}
	}
	public void applyOp(Document doc, String query, Node config, XMLPatchOp op) throws XPathExpressionException {
		applyOp(doc, getNodes(doc, query), config, op);
	}
	public void applyPatch(File patchFile, File rootDir) throws Exception {
		DocumentBuilderFactory patFac = DocumentBuilderFactory.newInstance();
		DocumentBuilder patBuilder = patFac.newDocumentBuilder();
		Document patchDoc = patBuilder.parse(new FileInputStream(patchFile));
		Node n_xmlp = patchDoc.getFirstChild();
		if (!n_xmlp.getNodeName().equals("xmlp"))
			throw new RuntimeException("Root patch node must be named \"xmlp\""); 

		for (Node n_xmlp_patch = n_xmlp.getFirstChild(); n_xmlp_patch != null; n_xmlp_patch = n_xmlp_patch.getNextSibling()) {
			if (n_xmlp_patch.getNodeName().equals("patch")) {
				NamedNodeMap n_xmlp_patch_attr = n_xmlp_patch.getAttributes();

				Node n = null;
				String p_target = null;
				String p_query = null;
				boolean p_dummied = false;
				
				//get target...
				n = n_xmlp_patch_attr.getNamedItem("target");
				if (n != null) p_target = n.getNodeValue();

				//get query...
				n = n_xmlp_patch_attr.getNamedItem("query");
				if (n != null) p_query = n.getNodeValue();
				
				//get dummied...
				n = n_xmlp_patch_attr.getNamedItem("dummy");
				if (n != null) p_dummied = Boolean.parseBoolean(n.getNodeValue());
				
				if (p_target == null) { System.err.println("Patch has no target!"); continue; }
				if (p_query  == null) { System.err.println("Patch has no query!"); continue; }

				System.out.println("Patching \""+p_target+"\":\""+p_query+"\"...");
				
				Document doc = getDoc(rootDir, p_target, p_dummied);

				NodeList nodes = getNodes(doc, p_query);
				for (Node n_xmlp_patch_op = n_xmlp_patch.getFirstChild(); n_xmlp_patch_op != null; n_xmlp_patch_op = n_xmlp_patch_op.getNextSibling()) {
					if (n_xmlp_patch_op.getNodeName().equals("op")) {
						NamedNodeMap n_xmlp_patch_op_attr = n_xmlp_patch_op.getAttributes();

						String p_op = null;

						//get op...
						n = n_xmlp_patch_op_attr.getNamedItem("id");
						if (n != null) p_op = n.getNodeValue();

						if (p_op == null) { System.err.println("Op has no id!"); continue; }

						XMLPatchOp op = opList.get(p_op);
						if (op == null) { System.err.println("Unrecognized op! (\""+p_op+"\")"); continue; }

						System.out.println("Excuting op \""+p_op+"\"");
						applyOp(doc, nodes, n_xmlp_patch_op, op);
					}
				}
			}
		}
	}
	public void writeDocMap(File outDir) throws Exception {
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");

		for (Iterator<Entry<String, XMLPDoc>> iter = docMap.entrySet().iterator(); iter.hasNext();) {
			Entry i = iter.next();
			String path = (String)i.getKey();
			XMLPDoc doc = (XMLPDoc)i.getValue();
			
			//initialize StreamResult with File object to save to file
			File outFile = new File(outDir, path);
			System.out.println("Writing \""+path+"\""+(doc.dummied?" (dummied)":"")+"...");
			
			OutputStream os = new FileOutputStream(outFile);
			if (doc.dummied)
				os = new DummyOutputStream(os);
			
			StreamResult result = new StreamResult(new OutputStreamWriter(os));
			Source source = new DOMSource(doc.doc);
			transformer.transform(source, result);
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
	public static interface XMLPatchOp {
		public void apply(Node config, Node target);
	}
	public static class XMLPatchOpPrintValue implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			System.out.println(target.getNodeName()+"("+getNameFromType(target.getNodeType())+") = "+target.getNodeValue());
		}
	}
	public static class XMLPatchOpPrint implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			System.out.println(target.toString());
		}
	}
	public static class XMLPatchOpSet implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			NamedNodeMap attr = config.getAttributes();
			if (attr == null) throw new IllegalArgumentException("Op had no attributes!");
			Node a_value = attr.getNamedItem("value");
			if (a_value == null) throw new IllegalArgumentException("Expected 'value' attribute!");
			String value = a_value.getNodeValue();
			
			String org = target.getNodeValue();
			target.setNodeValue(value);
			System.out.println("\""+org+"\" > \""+target.getNodeValue()+"\"");
		}
	}
	public static class XMLPatchOpAddSet implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			NamedNodeMap attr = config.getAttributes();
			if (attr == null) throw new IllegalArgumentException("Op had no attributes!");
			Node a_value = attr.getNamedItem("value");
			if (a_value == null) throw new IllegalArgumentException("Expected 'value' attribute!");
			double value = Double.parseDouble(a_value.getNodeValue());
			
			double org = Double.parseDouble(target.getNodeValue());
			target.setNodeValue(Double.toString(org+value));
			System.out.println("\""+org+"\" > \""+target.getNodeValue()+"\"");
		}
	}
	public static class XMLPatchOpSubSet implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			NamedNodeMap attr = config.getAttributes();
			if (attr == null) throw new IllegalArgumentException("Op had no attributes!");
			Node a_value = attr.getNamedItem("value");
			if (a_value == null) throw new IllegalArgumentException("Expected 'value' attribute!");
			double value = Double.parseDouble(a_value.getNodeValue());
			
			double org = Double.parseDouble(target.getNodeValue());
			target.setNodeValue(Double.toString(org-value));
			System.out.println("\""+org+"\" > \""+target.getNodeValue()+"\"");
		}
	}
	public static class XMLPatchOpMulSet implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			NamedNodeMap attr = config.getAttributes();
			if (attr == null) throw new IllegalArgumentException("Op had no attributes!");
			Node a_value = attr.getNamedItem("value");
			if (a_value == null) throw new IllegalArgumentException("Expected 'value' attribute!");
			double value = Double.parseDouble(a_value.getNodeValue());
			
			double org = Double.parseDouble(target.getNodeValue());
			target.setNodeValue(Double.toString(org*value));
			System.out.println("\""+org+"\" > \""+target.getNodeValue()+"\"");
		}
	}
	public static class XMLPatchOpDivSet implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			NamedNodeMap attr = config.getAttributes();
			if (attr == null) throw new IllegalArgumentException("Op had no attributes!");
			Node a_value = attr.getNamedItem("value");
			if (a_value == null) throw new IllegalArgumentException("Expected 'value' attribute!");
			double value = Double.parseDouble(a_value.getNodeValue());
			
			double org = Double.parseDouble(target.getNodeValue());
			target.setNodeValue(Double.toString(org/value));
			System.out.println("\""+org+"\" > \""+target.getNodeValue()+"\"");
		}
	}
	public static class XMLPatchOpFloor implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			double sig = 0;
			boolean direct = false;
			NamedNodeMap attr = config.getAttributes();
			if (attr != null) {
				Node a_value = attr.getNamedItem("direct");
				if (a_value != null)
					direct = Boolean.parseBoolean(a_value.getNodeValue());
				a_value = attr.getNamedItem("sig");
				if (a_value != null)
					sig = Double.parseDouble(a_value.getNodeValue());
			}
			double pow = direct ? sig : Math.pow(10, sig);
			
			double org = Double.parseDouble(target.getNodeValue());
			if (Math.abs(pow-1d)<0.00000001)
				target.setNodeValue(Integer.toString((int)Math.floor(org)));
			else
				target.setNodeValue(Double.toString(Math.floor(org*pow)/pow));
			System.out.println("\""+org+"\" > \""+target.getNodeValue()+"\"");
		}
	}
	public static class XMLPatchOpCeil implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			double sig = 0;
			boolean direct = false;
			NamedNodeMap attr = config.getAttributes();
			if (attr != null) {
				Node a_value = attr.getNamedItem("direct");
				if (a_value != null)
					direct = Boolean.parseBoolean(a_value.getNodeValue());
				a_value = attr.getNamedItem("sig");
				if (a_value != null)
					sig = Double.parseDouble(a_value.getNodeValue());
			}
			double pow = direct ? sig : Math.pow(10, sig);
			
			double org = Double.parseDouble(target.getNodeValue());
			if (Math.abs(pow-1d)<0.00000001)
				target.setNodeValue(Integer.toString((int)Math.ceil(org)));
			else
				target.setNodeValue(Double.toString(Math.ceil(org*pow)/pow));
			System.out.println("\""+org+"\" > \""+target.getNodeValue()+"\"");
		}
	}
	public static class XMLPatchOpRound implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			double sig = 0;
			boolean direct = false;
			NamedNodeMap attr = config.getAttributes();
			if (attr != null) {
				Node a_value = attr.getNamedItem("direct");
				if (a_value != null)
					direct = Boolean.parseBoolean(a_value.getNodeValue());
				a_value = attr.getNamedItem("sig");
				if (a_value != null)
					sig = Double.parseDouble(a_value.getNodeValue());
			}
			double pow = direct ? sig : Math.pow(10, sig);
			
			double org = Double.parseDouble(target.getNodeValue());
			if (Math.abs(pow-1d)<0.00000001)
				target.setNodeValue(Integer.toString((int)Math.round(org)));
			else
				target.setNodeValue(Double.toString(Math.round(org*pow)/pow));
			System.out.println("\""+org+"\" > \""+target.getNodeValue()+"\"");
		}
	}
	public static class XMLPatchOpSqrt implements XMLPatchOp {
		@Override public void apply(Node config, Node target) {
			double org = Double.parseDouble(target.getNodeValue());
			target.setNodeValue(Double.toString(Math.sqrt(org)));
			System.out.println("\""+org+"\" > \""+target.getNodeValue()+"\"");
		}
	}
	
}
