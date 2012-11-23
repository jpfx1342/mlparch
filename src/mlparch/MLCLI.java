/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

/**
 *
 * @author John Petska
 */
public class MLCLI {
	public static void printHelp() {
		System.out.println("Please select a subtool as follows: ");
		System.out.println("    java -jar path/to/MLPArch.jar <subtool> <args>");
		System.out.println("Available subtools:");
		System.out.println("    MLPArch (or mlpa)");
		System.out.println("    XMLPatch (or xmlp)");
	}
	public static void main(String[] args) throws Exception {
		if (args.length < 1 || args[0].equals("--help") || args[0].equals("-v") || args[0].equals("-?")) {
			printHelp();
		} else {
			String[] argsN = new String[args.length-1];
			System.arraycopy(args, 1, argsN, 0, argsN.length);
			
			String tool = args[0].toLowerCase();
			       if (tool.equals("mlparch") || tool.equals("mlpa")) {
				MLPA_CLI.main(argsN);
			} else if (tool.equals("xmlpatch") || tool.equals("xmlp")) {
				XMLP_CLI.main(argsN);
			} else {
				System.err.println("Unrecognized subtool: "+tool);
				printHelp();
			}
		}
		System.exit(0);
	}
}
