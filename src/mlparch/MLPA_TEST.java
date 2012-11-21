/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

import java.io.File;
import java.io.IOException;
import mlparch.MLPArch.MLPFileEntry;

/**
 *
 * @author John Petska
 */
public class MLPA_TEST {
	public static void main(String[] args) throws Exception {
		String archName = "main.1050.com.gameloft.android.ANMP.GloftPOHM.obb";
		if (args.length >= 1) archName = args[0];
		File archFile = new File(archName);
		
		MLPArch arch = new MLPArch(archFile);
		
		System.out.println("Reading MLPArch at \""+archFile.getPath()+"\"");
		
		System.out.print("Reading header...");
			arch.loadHeaderFromArchive();
		System.out.println("done.");
		System.out.println("\tIndex Start: "+arch.indexOffset);
		
		System.out.print("Reading index...");
			arch.loadIndexFromArchive();
		System.out.println("done.");
		System.out.println("\tFile Count: "+arch.index.size());
		
		/*
		System.out.println("Dumping Index...");
		for (int i = 0; i < arch.index.size(); i++)
			System.out.println(i+": "+arch.index.get(i).toString());
		// */
		
		/*
		System.out.println("Extracting first entry...");
			File destFolder = new File("extract"); destFolder.mkdir();
			System.out.println("Destination: \""+destFolder.getPath()+"\"");
			MLPFileEntry entry = arch.index.get(0);
			System.out.print("Extracting \""+entry.path+"\" ("+entry.size()+" bytes)...");
				arch.extractFile(entry, destFolder);
			System.out.println("done.");
		// */
		
		System.out.println("Extracting archive...");
			File destFolder = new File("extract"); destFolder.mkdir();
			System.out.println("Destination: \""+destFolder.getPath()+"\"");
			arch.extractArchive(destFolder);
	}
}
