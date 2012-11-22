/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

import java.io.File;
import java.io.FileInputStream;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import mlparch.XMLPatch.XMLPatchOp;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author john
 */
public class XMLP_CLI {
	public static void showHelp() {
		System.out.println("XMLPatch XML patching utility");
		System.out.println("Public domain code, released 2012");
		System.out.println("Options:");
		System.out.println("    -p - apply a patch file (default)");
		System.out.println("    -q <arg> - do an XPath query");
		System.out.println("    -t <arg> - set target file for patch or query (default \"xmlpatch.xml\")");
		System.out.println("    -p <arg> - set root directory for patch files (default \"extract\")");
		System.out.println("    -v - show this help");
		System.out.println("    -? - show this help");
		System.out.println("    --help - show this help");
	}
	public static void main(String[] args) throws Exception {
		String targName = "xmlpatch.xml";
		String pdirName = "extract";
		String query = null;
		int mode = 0; //0 == patch, 1 == query
		Map<String, XMLPatchOp> opList = new HashMap<String, XMLPatchOp>();
		opList.put("print", new XMLPatchOp() {
			@Override public void apply(NamedNodeMap config, Node target) {
				System.out.println(target.toString());
			}
		});
		
		for (int i = 0; i < args.length; i++) {
			String arg0 = args[i];
			if (arg0.startsWith("--")) {
				//long option
				arg0 = arg0.substring(2);
				
				if (arg0.equals("help")) {
					showHelp(); System.exit(0);
				} else {
					throw new IllegalArgumentException("Unrecognized long option: '"+arg0+"'.");
				}
			} else if (arg0.charAt(0) == '-') {
				//short option
				arg0 = arg0.substring(1);
				
				for (int j = 0; j < arg0.length(); j++) {
					//short optiona may be stacked
					char opt = arg0.charAt(j);
					
					switch (opt) {
						case 'v':
						case '?':
							showHelp(); System.exit(0);
						case 'p':
							mode = 0;
							break;
						case 'q':
							if (j!=arg0.length()-1)
								throw new IllegalArgumentException("'q' short option must be last in a stack!");
							if (++i >= args.length)
								throw new IllegalArgumentException("Expected another bare argument after 'q'!");
							mode = 1;
							query = args[i];
							break;
						case 't':
							if (j!=arg0.length()-1)
								throw new IllegalArgumentException("'t' short option must be last in a stack!");
							if (++i >= args.length)
								throw new IllegalArgumentException("Expected another bare argument after 't'!");
							targName = args[i];
							break;
						case 'f':
							if (j!=arg0.length()-1)
								throw new IllegalArgumentException("'f' short option must be last in a stack!");
							if (++i >= args.length)
								throw new IllegalArgumentException("Expected another bare argument after 'f'!");
							pdirName = args[i];
							break;
						default:
							throw new IllegalArgumentException("Unrecognized short option: '"+opt+"'.");
					}
				}
			} else {
				//bare argument
				throw new IllegalArgumentException("Unrecognized bare argument: '"+arg0+"'.");
			}
		}
		
		if (mode == 0) {
			//patch mode
			System.out.println("Reading patch file \""+targName+"\"...");
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document patchDoc = builder.parse(targName);
			Node n_xmlp = patchDoc.getFirstChild();
			if (!n_xmlp.getNodeName().equals("xmlp"))
				throw new RuntimeException("Root patch node must be named \"xmlp\""); 
			
			HashMap<String, XMLPatch> patMap = new HashMap<String, XMLPatch>();
			DocumentBuilderFactory docFac = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder docBuilder = docFac.newDocumentBuilder();
			for (Node n_xmlp_patch = n_xmlp.getFirstChild(); n_xmlp_patch != null; n_xmlp_patch = n_xmlp_patch.getNextSibling()) {
				if (n_xmlp_patch.getNodeName().equals("patch")) {
					NamedNodeMap n_xmlp_patch_attr = n_xmlp_patch.getAttributes();
					
					Node n = null;
					String p_target = null;
					String p_query = null;
					
					//get target...
					n = n_xmlp_patch_attr.getNamedItem("target");
					if (n != null) p_target = n.getNodeValue();
					
					//get query...
					n = n_xmlp_patch_attr.getNamedItem("query");
					if (n != null) p_query = n.getNodeValue();

					if (p_target == null) { System.err.println("Patch has no target!"); continue; }
					if (p_query  == null) { System.err.println("Patch has no query!"); continue; }
					
					XMLPatch pat = patMap.get(targName);
					if (pat == null) {
						File target = new File(pdirName, targName);
						if (!target.exists() || !target.isFile())
							throw new RuntimeException("Couldn't locate target!");
						Document doc = docBuilder.parse(new FileInputStream(target));
						pat = new XMLPatch(doc);
						patMap.put(targName, pat);
					}
					
					System.out.println("Patching \""+p_target+"\":\""+p_query+"\"...");
					
					NodeList nodes = pat.getNodes(p_query);
					for (Node n_xmlp_patch_op = n_xmlp_patch.getFirstChild(); n_xmlp_patch_op != null; n_xmlp_patch_op = n_xmlp_patch_op.getNextSibling()) {
						NamedNodeMap n_xmlp_patch_op_attr = n_xmlp_patch_op.getAttributes();
						
						String p_op = null;
						
						//get op...
						n = n_xmlp_patch_op_attr.getNamedItem("id");
						if (n != null) p_op = n.getNodeValue();

						if (p_op == null) { System.err.println("Op has no id!"); continue; }
						
						XMLPatchOp op = opList.get(p_query);
						if (op == null) { System.err.println("Unrecognized op!"); continue; }
						
						pat.applyPatch(nodes, n_xmlp_patch_op_attr, op);
					}
				}
			}
		} else {
			//query mode	
			System.out.println("Querying \""+query+"\" from \""+targName+"\"...");
				//String query = "/GameObjects/GameObject[@Category=\"Pony\"]/@ID";
				//String query = "/GameObjects/GameObject[@Category=\"Pony_House\"]/Construction/@ConstructionTime";
				XMLPatch patcher = new XMLPatch(targName);
				patcher.applyPatch(query, null, new XMLPatch.PrintXMLActor());
		}
	}
}
