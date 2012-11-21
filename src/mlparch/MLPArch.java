/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.ArrayList;

/**
 * This object represents an MLPFS, as used by the My Little Pony game by 
 * Gameloft.
 * 
 * @author John Petska
 */
public class MLPArch {
	public static class MLPFileEntry {
		public int startOffset;
		public int endOffset;
		public int magic0;
		public int magic1;
		public String path;
		
		public MLPFileEntry(int startOffset, int endOffset, int magic0, int magic1, String path) {
			this.startOffset = startOffset;
			this.endOffset = endOffset;
			this.magic0 = magic0;
			this.magic1 = magic1;
			this.path = path;
		}
		
		public int size() { return endOffset-startOffset; }
		
		@Override
		public String toString() {
			return "\""+path+"\" ("+startOffset+" - "+endOffset+") {"+size()+"} ["+magic0+", "+magic1+"]";
		}
	}
	
	/** The starting index of the file index. **/
	public int indexOffset;
	/** The file index for this archive. **/
	public ArrayList<MLPFileEntry> index;
	/** The file location backing this archive. This is the file we will read and
	 * write from when loading/saving the archive. **/
	public File archFile;
	public RandomAccessFile archAcc;
	
	
	public Charset indexCharset;
	{
		try {
			indexCharset = Charset.forName("UTF-8");
		} catch (Exception ex1) {
			printlnerr("Couldn't locate index charset \"UTF-8\": "+ex1.getLocalizedMessage());
			try {
				indexCharset = Charset.forName("UTF8");
			} catch (Exception ex2) {
				printlnerr("Couldn't locate index charset \"UTF8\": "+ex2.getLocalizedMessage());
				indexCharset = Charset.defaultCharset();
				printlnerr("Falling back on: "+indexCharset.name());
			}
		}
	}
	public boolean readonly = true;
	public PrintStream stdout = System.out;
	public void printout(String s)   { if (stdout != null) stdout.print  (s); }
	public void printlnout(String s) { if (stdout != null) stdout.println(s); }
	public PrintStream stderr = System.err;
	public void printerr(String s)   { if (stderr != null) stderr.print  (s); }
	public void printlnerr(String s) { if (stderr != null) stderr.println(s); }
	
	/** This option forces the header to be a certain length. If this is above
	 * 0, the header is always considered to be this long. This means when reading
	 * the whole header will be parsed as the index index, and when writing, the
	 * header will be prepended with zeros to fit this length. This defaults to
	 * 9, matching current Gameloft data. **/
	public int compatFixedHeaderSize = 9;
	public char compatNewLineChar = 0x0A; //LF
	public char compatFieldChar = 0x20; //Space
	public int compatMaxLineLength = 1024;
	public int compatWriteBufferSize = 1024;
	
	/** This constructor simply sets the archive file. **/
	public MLPArch(File archFile) {
		this.archFile = archFile;
	}
	
	public void prepareAccess() throws FileNotFoundException {
		if (archAcc == null)
			archAcc = new RandomAccessFile(archFile, readonly?"r":"rw");
	}
	
	/** This method attempts to load the header from the archive. **/
	public void loadHeaderFromArchive() throws FileNotFoundException, IOException {
		prepareAccess();
		//The MLP archive header is really, stupidly simple. It's a series of
		//plain-text decimal digits, which indicate the starting position of the index.
		//It is unknown whether this is can be a variable length, but all Gameloft
		//released versions of the file have a 9 byte header.
		if (compatFixedHeaderSize > 0) {
			//we're reading as a fixed header.
			//read the header
			byte[] data = new byte[compatFixedHeaderSize];
			archAcc.read(data);
			
			//trim the header (easy because it's fixed length)
			String number = new String(data, Charset.forName("UTF8"));
			
			//get the index offset
			indexOffset = Integer.parseInt(number, 10);
		} else {
			//we're reading as a variable length header.
			//basically the same thing, except we read 32 bytes, and search that
			//string for our number
			byte[] data = new byte[32];
			archAcc.read(data);
			
			//trim the header
			String number = new String(data, Charset.forName("UTF8"));
			int lastDigit = 0;
			for (int i = 0; i < number.length(); i++)
				if (!Character.isDigit(number.charAt(i)))
					{ lastDigit = i; break; }
			number = number.substring(0, lastDigit);
			
			//get the index offset
			indexOffset = Integer.parseInt(number, 10);
		}
	}
	public void loadIndexFromArchive() throws FileNotFoundException {
		prepareAccess();
		
		//The index is a series of file entries, separated by newlines.
		//There is no file count, so you just have to keep reading until you
		//reach the end.
		//note that we expect the index to end with a newline.
		index = new ArrayList<MLPFileEntry>();
		byte[] line = new byte[compatMaxLineLength];
		try {
			//first seek to the index
			archAcc.seek(indexOffset);
			
			while (true) {
				//System.out.println("Reading index "+index.size()+" at "+archAcc.getFilePointer()+"/"+archAcc.length());
				//an index entry is composed of 5 parts, separated by spaces.
				//an index entry has no size field so you just have to read
				//until you hit a newline. Here are the parts of an index.
				// - The Start Offset - This is the beginning of the file, as
				//measured from the beginning of the archive.
				// - The End Offset - This is the end of the file, as
				//measured from the beginning of the archive.
				// - The Magic 0 - This field is unknown.
				// - The Magic 1 - This field is unknown, but always seems to be
				//equal to the start offset.
				// - The Path - This field indicated the full path to the file
				//in the archive.
				
				boolean eof = false;
				//copy to our line buffer
				int pos0 = 0;
				while (true) {
					try {
						byte b = archAcc.readByte();
						if (b == compatNewLineChar)
							break;
						line[pos0++] = b;
					} catch (IOException e) { eof = true; break; }
				}
				int len = pos0; int pos1 = -1;
				String field = null;
				if (len < 1) break;
				
				//prepare all the fields
				int fieldStartOffset = 0;
				int fieldEndOffset = 0;
				int fieldMagic0 = 0;
				int fieldMagic1 = 0;
				String fieldPath = null;
				
				//read the first field
				//feel it out
				pos0 = ++pos1; //pos0 = start of field, pos1 = end of field
				while (pos1 < compatMaxLineLength && line[pos1] != compatFieldChar) pos1++;
				field = new String(line, pos0, pos1-pos0, indexCharset);
				//parse it
				fieldStartOffset = Integer.parseInt(field, 10);
				
				//read the first field
				//feel it out
				pos0 = ++pos1; //pos0 = start of field, pos1 = end of field
				while (pos1 < compatMaxLineLength && line[pos1] != compatFieldChar) pos1++;
				field = new String(line, pos0, pos1-pos0, indexCharset);
				//parse it
				fieldEndOffset = Integer.parseInt(field, 10);
				
				//read the first field
				//feel it out
				pos0 = ++pos1; //pos0 = start of field, pos1 = end of field
				while (pos1 < compatMaxLineLength && line[pos1] != compatFieldChar) pos1++;
				field = new String(line, pos0, pos1-pos0, indexCharset);
				//parse it
				fieldMagic0 = Integer.parseInt(field, 10);
				
				//read the first field
				//feel it out
				pos0 = ++pos1; //pos0 = start of field, pos1 = end of field
				while (pos1 < compatMaxLineLength && line[pos1] != compatFieldChar) pos1++;
				field = new String(line, pos0, pos1-pos0, indexCharset);
				//parse it
				fieldMagic1 = Integer.parseInt(field, 10);
				
				//everything else is the last field.
				pos0 = ++pos1; //pos0 = start of field, pos1 = end of field
				pos1 = len;
				field = new String(line, pos0, pos1-pos0, indexCharset);
				//parse it
				fieldPath = field;
				
				//if (fieldStartOffset != fieldMagic1)
				//	printlnout("startOffset/magic2 field mismatch? "+index.size()+": "+fieldStartOffset+" != "+fieldMagic1);
				
				MLPFileEntry entry = new MLPFileEntry(fieldStartOffset, fieldEndOffset, fieldMagic0, fieldMagic1, fieldPath);
				//printlnout(""+entry.size()+" / "+entry.magic0+" = "+((float)entry.size()/entry.magic0));
				index.add(entry);
				
				if (eof) break;
			}
		} catch (IOException ex) {
			//we reached the end of file. probably.
		}
	}
	/** Extracts a single file from the archive. **/
	public void extractFile(MLPFileEntry entry, File destFolder) throws FileNotFoundException, IOException {
		prepareAccess();
		
		File destFile = new File(destFolder, entry.path);
		destFile.getParentFile().mkdirs();
		
		OutputStream os = new FileOutputStream(destFile);
		//for all the data...
		archAcc.seek(entry.startOffset);
		byte[] buffer = new byte[compatWriteBufferSize];
		int pos = 0;
		while (true) {
			int rem = entry.size()-pos;
			if (rem <= 0) break;
			int read = archAcc.read(buffer, 0, rem < compatWriteBufferSize ? rem : compatWriteBufferSize);
			if (read <= 0) break;
			os.write(buffer, 0, read);
			pos += read;
		}
		os.flush(); os.close();
	}
	public void extractArchive(File destFolder) throws FileNotFoundException, IOException {
		NumberFormat format = NumberFormat.getPercentInstance();
		for (int i = 0; i < index.size(); i++) {
			MLPFileEntry entry = index.get(i);
			printout("Extracting "+i+"/"+index.size()+" ("+format.format((float)i/index.size())+"): \""+entry.path+"\" ("+entry.size()+" bytes)...");
				extractFile(entry, destFolder);
			printlnout("done.");
		}
	}
}
