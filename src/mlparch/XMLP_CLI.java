/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
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
		printlnout(0, "    -p - patch mode (default)");
		printlnout(0, "    -q <arg> - XPath query mode");
		printlnout(0, "    -t <arg> - set target file for patch or query (default \"xmlpatch.xml\")");
		printlnout(0, "    -r <arg> - set root directory for patch operation (default auto-select \".GloftPOHM\")");
		printlnout(0, "    -o <arg> - write updated files to a different folder for patch operation (default=rootDir)");
		printlnout(0, "    -f - activate fake mode (don't actually write files)");
		printlnout(0, "    -v - increase verbosity (may be repeated)");
		printlnout(0, "    -? - show this help");
		printlnout(0, "    --help - show this help");
	}
	public static void errFatal(String error, int code) {
		System.err.print("Fatal Error: "); System.err.println(error);
		System.err.println("Cannot continue.");
		System.exit(code);
	}
	public static final int ERR_AUTOSELECT = 253;
	public static final int ERR_ARGUMENT = 254;
	
	public static void main(String[] args) throws Exception {
		String targName = "xmlpatch.xml";
		String pdirName = null;
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
					errFatal("Unrecognized long option: '"+arg0+"'.", ERR_ARGUMENT);
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
								errFatal("'q' short option must be last in a stack!", ERR_ARGUMENT);
							if (++i >= args.length)
								errFatal("Expected another bare argument after 'q'!", ERR_ARGUMENT);
							mode = 1;
							query = args[i];
							break;
						case 't':
							if (j!=arg0.length()-1)
								errFatal("'t' short option must be last in a stack!", ERR_ARGUMENT);
							if (++i >= args.length)
								errFatal("Expected another bare argument after 't'!", ERR_ARGUMENT);
							targName = args[i];
							break;
						case 'r':
							if (j!=arg0.length()-1)
								errFatal("'f' short option must be last in a stack!", ERR_ARGUMENT);
							if (++i >= args.length)
								errFatal("Expected another bare argument after 'f'!", ERR_ARGUMENT);
							pdirName = args[i];
							break;
						case 'o':
							if (j!=arg0.length()-1)
								errFatal("'o' short option must be last in a stack!", ERR_ARGUMENT);
							if (++i >= args.length)
								errFatal("Expected another bare argument after 'o'!", ERR_ARGUMENT);
							outdName = args[i];
							break;
						case 'f':
							fakeMode = true;
							break;
						default:
							errFatal("Unrecognized short option: '"+opt+"'.", ERR_ARGUMENT);
					}
				}
			} else {
				//bare argument
				errFatal("Unrecognized bare argument: '"+arg0+"'.", ERR_ARGUMENT);
			}
		}
		if (pdirName == null) {
			System.out.println("Root target directory not specified, auto-selecting...");
			
			File local = new File(".");
			if (!local.isDirectory())
				throw new IllegalStateException("Current directory isn't a directory?"); //huh. current directory isn't a directory
			File[] files = local.listFiles(new FilenameFilter() {
				@Override public boolean accept(File dir, String name) {
					return name.endsWith(".GloftPOHM");
				}
			});
			if (files == null || files.length == 0)
				errFatal("Cannot auto-select root, no possible directories in current directory (*.GloftPOHM)", ERR_AUTOSELECT);
			if (files.length > 1)
				errFatal("Cannot auto-select root, too many possible directories in current directory (*.GloftPOHM) (found "+files.length+")", ERR_AUTOSELECT);

			pdirName = files[0].getPath();
			
			System.out.println("Auto-selected root: "+pdirName);
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
