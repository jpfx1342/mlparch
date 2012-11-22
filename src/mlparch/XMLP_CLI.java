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
		System.out.println("    -r <arg> - set root directory for patch operation (default \"extract\")");
		System.out.println("    -o <arg> - write updated files to a different folder for patch operation (default=rootDir)");
		System.out.println("    -v - show this help");
		System.out.println("    -? - show this help");
		System.out.println("    --help - show this help");
	}
	public static void main(String[] args) throws Exception {
		String targName = "xmlpatch.xml";
		String pdirName = "extract";
		String outdName = null;
		String query = null;
		int mode = 0; //0 == patch, 1 == query
		
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
						case 'r':
							if (j!=arg0.length()-1)
								throw new IllegalArgumentException("'f' short option must be last in a stack!");
							if (++i >= args.length)
								throw new IllegalArgumentException("Expected another bare argument after 'f'!");
							pdirName = args[i];
							break;
						case 'o':
							if (j!=arg0.length()-1)
								throw new IllegalArgumentException("'o' short option must be last in a stack!");
							if (++i >= args.length)
								throw new IllegalArgumentException("Expected another bare argument after 'o'!");
							outdName = args[i];
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
		if (outdName == null) outdName = pdirName;
		File targFile = new File(targName);
		File pdirFile = new File(pdirName);
		File outdFile = new File(outdName);
		
		if (mode == 0) {
			//patch mode
			System.out.println("Applying patch file \""+targName+"\" to \""+pdirName+"\"...");
				XMLPatch patcher = new XMLPatch();
				patcher.applyPatch(targFile, pdirFile);
			System.out.println("Writing patched documents to \""+outdName+"\"...");
				outdFile.mkdirs();
				patcher.writeDocMap(outdFile);
		} else {
			//query mode	
			System.out.println("Querying \""+query+"\" from \""+targName+"\"...");
				//String query = "/GameObjects/GameObject[@Category=\"Pony\"]/@ID";
				//String query = "/GameObjects/GameObject[@Category=\"Pony_House\"]/Construction/@ConstructionTime";
				XMLPatch patcher = new XMLPatch();
				patcher.applyOp(patcher.getDoc(null, targName), query, null, new XMLPatch.XMLPatchOpPrint());
		}
	}
}
