/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
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
	public static int verbosity = 0;
	
	public static PrintStream stdout = System.out;
	public static void printout(int l, String s)   { if (stdout != null && l <= verbosity) stdout.print  (s); }
	public static void printlnout(int l, String s) { if (stdout != null && l <= verbosity) stdout.println(s); }
	public static PrintStream stderr = System.err;
	public static void printerr(int l, String s)   { if (stderr != null && l <= verbosity) stderr.print  (s); }
	public static void printlnerr(int l, String s) { if (stderr != null && l <= verbosity) stderr.println(s); }
	
	public static void showHelp() {
		printlnout(0, "XMLPatch XML patching utility");
		printlnout(0, "Public domain code, released 2012");
		printlnout(0, "Options:");
		printlnout(0, "    -p - apply a patch file (default)");
		printlnout(0, "    -q <arg> - do an XPath query");
		printlnout(0, "    -t <arg> - set target file for patch or query (default \"xmlpatch.xml\")");
		printlnout(0, "    -r <arg> - set root directory for patch operation (default \"extract\")");
		printlnout(0, "    -o <arg> - write updated files to a different folder for patch operation (default=rootDir)");
		printlnout(0, "    -f - activate fake mode (don't actually write files)");
		printlnout(0, "    -v - increase verbosity (may be repeated)");
		printlnout(0, "    -? - show this help");
		printlnout(0, "    --help - show this help");
	}
	public static void main(String[] args) throws Exception {
		String targName = "xmlpatch.xml";
		String pdirName = "extract";
		String outdName = null;
		String query = null;
		int mode = 0; //0 == patch, 1 == query
		boolean fakeMode = false;
		verbosity = 0;
		
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
						case '?':
							showHelp(); System.exit(0);
						case 'v':
							verbosity++; break;
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
						case 'f':
							fakeMode = true;
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
			printlnout(0, "Applying patch file \""+targName+"\" to \""+pdirName+"\"...");
				XMLPatch patcher = new XMLPatch(verbosity);
				patcher.applyPatch(targFile, pdirFile);
			if (!fakeMode) {
				printlnout(0, "Writing patched documents to \""+outdName+"\"...");
					outdFile.mkdirs();
					patcher.writeDocMap(outdFile);
			}
		} else {
			//query mode	
			printlnout(1, "Querying \""+query+"\" from \""+targName+"\"...");
				//String query = "/GameObjects/GameObject[@Category=\"Pony\"]/@ID";
				//String query = "/GameObjects/GameObject[@Category=\"Pony_House\"]/Construction/@ConstructionTime";
				XMLPatch patcher = new XMLPatch(verbosity);
				patcher.applyOp(patcher.getDoc(null, targName, null), query, null, new XMLPatch.XMLPatchOpPrint(patcher));
		}
	}
}
