/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.regex.Pattern;
import mlparch.MLPArch.MLPFileEntry;

/**
 *
 * @author John Petska
 */
public class MLPA_CLI {
	public static int verbosity = 0;
	
	public static PrintStream stdout = System.out;
	public static void printout(int l, String s)   { if (stdout != null && l <= verbosity) stdout.print  (s); }
	public static void printlnout(int l, String s) { if (stdout != null && l <= verbosity) stdout.println(s); }
	public static PrintStream stderr = System.err;
	public static void printerr(int l, String s)   { if (stderr != null && l <= verbosity) stderr.print  (s); }
	public static void printlnerr(int l, String s) { if (stderr != null && l <= verbosity) stderr.println(s); }
	
	public static void showHelp() {
		printlnout(0, "MLPArch packing/unpacking utility");
		printlnout(0, "Public domain code, released 2012");
		printlnout(0, "Options:");
		printlnout(0, "    -u - unpack mode (default)");
		printlnout(0, "    -p - pack mode");
		printlnout(0, "    -l - list mode ");
		printlnout(0, "    --csv - list entries as csv (also supresses most output)");
		printlnout(0, "    -a <arg> - specify archive location (default auto-selects \"*.obb\")");
		printlnout(0, "    -f <arg> - specify pack/unpack location (default <archive path>-\".obb\")");
		printlnout(0, "    -s <arg> - pack/unpack only a single file");
		printlnout(0, "    -r <arg> - pack/unpack only files matching this regex");
		//printlnout(0, "    -m <arg> - pack/unpack only files matching this wildcard pattern (*, ?)");
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
		//Where the archive is (going to be)
		String archName = null;
		//Where the extracted files are (going to be)
		String packName = null;
		int mode = 0; //0 == unpack, 1 == pack, 2 == list
		int matchMode = 0; //0 == all, 1 == single, 2 == regex, 3 == wildcard
		String matchPat = null;
		verbosity = 0;
		int listFmt = 0; //0 == normal, 1 == csv
		
		for (int i = 0; i < args.length; i++) {
			String arg0 = args[i];
			if (arg0.startsWith("--")) {
				//long option
				arg0 = arg0.substring(2);
				
				if (arg0.equals("help")) {
					showHelp(); System.exit(0);
				} else if (arg0.equals("csv")) {
					mode = 2; //list
					listFmt = 1;
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
						case 'u':
							mode = 0; //unpack
							break;
						case 'p':
							mode = 1; //pack
							break;
						case 'l':
							mode = 2; //list
							break;
						case 'a':
							if (j!=arg0.length()-1 || ++i >= args.length)
								errFatal("Expected another bare argument after 'a'!", ERR_ARGUMENT);
							archName = args[i];
							break;
						case 'f':
							if (j!=arg0.length()-1 || ++i >= args.length)
								errFatal("Expected another bare argument after 'f'!", ERR_ARGUMENT);
							packName = args[i];
							break;
						case 's':
							if (j!=arg0.length()-1 || ++i >= args.length)
								errFatal("Expected another bare argument after 's'!", ERR_ARGUMENT);
							matchMode = 1;
							matchPat = args[i];
							break;
						case 'r':
							if (j!=arg0.length()-1 || ++i >= args.length)
								errFatal("Expected another bare argument after 'r'!", ERR_ARGUMENT);
							matchMode = 2;
							matchPat = args[i];
							break;
						case 'm':
							if (j!=arg0.length()-1 || ++i >= args.length)
								errFatal("Expected another bare argument after 'm'!", ERR_ARGUMENT);
							System.err.println("Attempting to use wildcard matching: This doesn't work very well, have fun!");
							matchMode = 3;
							matchPat = args[i];
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
		
		boolean autoArch = (archName == null);
		boolean autoPack = (packName == null);
		
		if (autoArch) {
			System.out.println("Archive file not specified, auto-selecting...");
			if (packName == null) {
				File local = new File(".");
				if (!local.isDirectory())
					throw new IllegalStateException("Current directory isn't a directory?"); //huh. current directory isn't a directory
				File[] files = local.listFiles(new FilenameFilter() {
					@Override public boolean accept(File dir, String name) {
						return name.endsWith(".obb");
					}
				});
				if (files == null || files.length == 0)
					errFatal("Cannot auto-select archive, no archives in current directory (*.obb)", ERR_AUTOSELECT);
				if (files.length > 1)
					errFatal("Cannot auto-select archive, too many archives in current directory (*.obb) (found "+files.length+")", ERR_AUTOSELECT);

				archName = files[0].getPath();
			} else {
				archName = packName+".obb";
			}
			
			System.out.println("Auto-selected archive: "+archName);
		}
		if (autoPack) {
			System.out.println("Pack directory not specified, auto-selecting...");
			
			int extp = archName.lastIndexOf('.');
			if (extp < 0)
				errFatal("Cannot auto-select output directory, archive doesn't have extension to remove", ERR_AUTOSELECT);
			packName = archName.substring(0, extp);
			
			System.out.println("Auto-selected pack directory: "+packName);
		}
		if (autoArch && mode == 1) {
			//create unique output name
			archName = archName.concat(".new");
		}
			
		
		File archFile = new File(archName);
		File packFile = new File(packName);
		MLPArch arch = new MLPArch(verbosity, archFile);
		switch (matchMode) {
			default:
			case 0: //all
				matchPat = null; break;
			case 1: //single
				matchPat = Pattern.quote(matchPat); break;
			case 2: //regex
				/* done. */ break;
			case 3: //wildcard
				matchPat = Pattern.quote(matchPat);
				matchPat = matchPat.replace("\\*", ".*");
				matchPat = matchPat.replace("\\?", ".");
				break;
		}
		Pattern pat = matchPat==null ? null : Pattern.compile(matchPat);
			
		if (mode == 0 || mode == 2) {
			//unpack or list
			int headerV = (mode == 2 && listFmt != 0) ?1:0;
			printlnout(headerV, (mode==0?"Unpacking":"Listing")+" MLPArch at \""+archFile.getPath()+"\" to \""+packFile.getPath()+"\".");
			
			printout(headerV, "Reading header...");
				arch.loadHeaderFromArchive();
			printlnout(headerV, "done.");
			printlnout(headerV, "\tIndex Start: "+arch.indexOffset);
			
			printout(headerV, "Reading index...");
				arch.loadIndexFromArchive();
			printlnout(headerV, "done.");
			printlnout(1, "\tFile Count: "+arch.index.size());
			
			if (mode == 0) {
				printlnout(headerV, "Unpacking archive...");
				packFile.mkdir();
				NumberFormat format = NumberFormat.getPercentInstance(); format.setMinimumFractionDigits(1); format.setMaximumFractionDigits(1);
				for (int i = 0; i < arch.index.size(); i++) {
					MLPFileEntry entry = arch.index.get(i);
					if (pat != null && !pat.matcher(entry.path).matches())
						continue; //skipping
					printout(1, "Unpacking "+(i+1)+"/"+arch.index.size()+" ("+format.format((float)(i+1)/arch.index.size())+"): \""+entry.path+"\" ("+entry.size()+" bytes)...");
						arch.unpackFile(entry, packFile);
					if (verbosity >= 1) printlnout(1, "done.");
					else { if (i%100==0) { printout(0, "("+i+"/"+arch.index.size()+")"); } else printout(0, "."); }
				}
				if (verbosity <= 0) printlnout(0, "");
			} else {
				printlnout(headerV, "Listing Index...");
				for (int i = 0; i < arch.index.size(); i++)
					if (pat == null || pat.matcher(arch.index.get(i).path).matches())
						if (listFmt == 1)
							printlnout(0, (i+1)+", "+arch.index.get(i).toCSV());
						else
							printlnout(0, (i+1)+": "+arch.index.get(i).toString());
			}
		} else if (mode == 1) {
			//pack
			printlnout(0, "Packing MLPArch at \""+archFile.getPath()+"\" from \""+packFile.getPath()+"\".");
			
			printout(0, "Building index...");
				arch.loadIndexFromFolder(packFile);
			printlnout(0, "done.");
			printlnout(0, "\tFile Count: "+arch.index.size());
			
			printlnout(0, "Writing header...");
				arch.writeHeaderToArchive();
			
			printlnout(0, "Packing files...");
				arch.writeFilesToArchive(packFile, pat);
			
			printlnout(0, "Writing index...");
				arch.writeIndexToArchive();
		}
		
	}
}
