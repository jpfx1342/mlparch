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
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * This object represents an MLPFS, as used by the My Little Pony game by 
 * Gameloft.
 * 
 * @author John Petska
 */
public class MLPArch {
	public int verbosity = 0;
	
	public PrintStream stdout = System.out;
	public void printout(int l, String s)   { if (stdout != null && l <= verbosity) stdout.print  (s); }
	public void printlnout(int l, String s) { if (stdout != null && l <= verbosity) stdout.println(s); }
	public PrintStream stderr = System.err;
	public void printerr(int l, String s)   { if (stderr != null && l <= verbosity) stderr.print  (s); }
	public void printlnerr(int l, String s) { if (stderr != null && l <= verbosity) stderr.println(s); }
	
	public static class MLPFileEntry {
		public long startOffset;
		public long endOffset;
		public long magic0;
		public long magic1;
		public String path;
		
		public MLPFileEntry(long startOffset, long endOffset, long magic0, long magic1, String path) {
			this.startOffset = startOffset;
			this.endOffset = endOffset;
			this.magic0 = magic0;
			this.magic1 = magic1;
			this.path = path;
		}
		
		public long size() { return endOffset-startOffset; }
		
		@Override
		public String toString() {
			return "\""+path+"\" ("+startOffset+" - "+endOffset+") {"+size()+"} ["+magic0+", "+magic1+"]";
		}
		public String toCSV() {
			return path+", "+startOffset+", "+endOffset+", "+size()+", "+magic0+", "+magic1;
		}
	}
	
	/** The starting index of the file index. **/
	public long indexOffset;
	/** The file index for this archive. **/
	public ArrayList<MLPFileEntry> index;
	/** The file location backing this archive. This is the file we will read and
	 * write from when loading/saving the archive. **/
	public File archFile;
	public RandomAccessFile archRead;
	public RandomAccessFile archWrite;
	
	public Charset indexCharset;
	{
		try {
			indexCharset = Charset.forName("UTF-8");
		} catch (Exception ex1) {
			printlnerr(0, "Couldn't locate index charset \"UTF-8\": "+ex1.getLocalizedMessage());
			try {
				indexCharset = Charset.forName("UTF8");
			} catch (Exception ex2) {
				printlnerr(0, "Couldn't locate index charset \"UTF8\": "+ex2.getLocalizedMessage());
				indexCharset = Charset.defaultCharset();
				printlnerr(0, "Falling back on: "+indexCharset.name());
			}
		}
	}
	public boolean readonly = true;
	
	/** This option forces the header to be a certain length. If this is above
	 * 0, the header is always considered to be this long. This means when reading
	 * the whole header will be parsed as the index index, and when writing, the
	 * header will be prepended with zeros to fit this length. This defaults to
	 * 9, matching current Gameloft data. **/
	public int compatFixedHeaderSize = 9;
	public char compatNewLineChar = 0x0A; //LF
	public char compatFieldChar = 0x20; //Space
	public int compatMaxLineLength = 1024;
	public int compatWriteBufferSize = 1024*16;
	/** Allow files to be extracted even if their path would put them outside
	 * the destination directory. Only set this to true if you know what you're
	 * doing, as a malicious archive could unpack files anywhere on your system. **/
	public boolean compatFullPaths = false;
	//path separator to use when creating archives
	public char compatPathSeparator = '/';
	
	/** This constructor simply sets the archive file. **/
	public MLPArch(int verbosity, File archFile) {
		this.verbosity = verbosity;
		this.archFile = archFile;
	}
	
	public static boolean isAncestorOf(File subject, File ancestor) {
		try {
			File target = ancestor.getCanonicalFile();
			File parent = subject.getCanonicalFile().getParentFile();
			while (parent != null) {
				if (parent.equals(target))
					return true;
				parent = parent.getParentFile();
			}
		} catch (IOException ex) {
			System.err.println("Exception checking ancestry of "+subject+" (target: "+ancestor+"): "+ex.getLocalizedMessage());
		}
		return false;
	}
	
	public void prepareRead() throws FileNotFoundException {
		if (archRead == null)
			archRead = new RandomAccessFile(archFile, "r");
	}
	public void prepareWrite() throws FileNotFoundException {
		if (archWrite == null) {
			if (archFile.exists())
				if (archFile.isFile())
					archFile.delete();
				else if (archFile.isDirectory())
					throw new FileNotFoundException("Target Archive for writing was a directory!");
			archWrite = new RandomAccessFile(archFile, "rw");
		}
	}
	
	/** This method attempts to load the header from the archive. **/
	public void loadHeaderFromArchive() throws FileNotFoundException, IOException {
		prepareRead();
		//The MLP archive header is really, stupidly simple. It's a series of
		//plain-text decimal digits, which indicate the starting position of the index.
		//It is unknown whether this is can be a variable length, but all Gameloft
		//released versions of the file have a 9 byte header.
		if (compatFixedHeaderSize > 0) {
			//we're reading as a fixed header.
			//read the header
			byte[] data = new byte[compatFixedHeaderSize];
			archRead.read(data);
			
			//trim the header (easy because it's fixed length)
			String number = new String(data, Charset.forName("UTF8"));
			
			//get the index offset
			indexOffset = Long.parseLong(number, 10);
		} else {
			//we're reading as a variable length header.
			//basically the same thing, except we read 32 bytes, and search that
			//string for our number
			byte[] data = new byte[32];
			archRead.read(data);
			
			//trim the header
			String number = new String(data, Charset.forName("UTF8"));
			int lastDigit = 0;
			for (int i = 0; i < number.length(); i++)
				if (!Character.isDigit(number.charAt(i)))
					{ lastDigit = i; break; }
			number = number.substring(0, lastDigit);
			
			//get the index offset
			indexOffset = Long.parseLong(number, 10);
		}
	}
	public void loadIndexFromArchive() throws FileNotFoundException {
		prepareRead();
		
		//The index is a series of file entries, separated by newlines.
		//There is no file count, so you just have to keep reading until you
		//reach the end.
		//note that we expect the index to end with a newline.
		index = new ArrayList<MLPFileEntry>();
		byte[] line = new byte[compatMaxLineLength];
		try {
			//first seek to the index
			archRead.seek(indexOffset);
			
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
						byte b = archRead.readByte();
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
	private void traverseAndAddToArray(File folder, ArrayList<String> files) { traverseAndAddToArray(folder, "", files); }
	private void traverseAndAddToArray(File folder, String prefix, ArrayList<String> files) {
		String[] children = folder.list();
		Arrays.sort(children); //natural sort is fine
		for (int i = 0; i < children.length; i++) {
			File f = new File(folder, children[i]);
			if (f.isDirectory()) {
				traverseAndAddToArray(f, prefix+compatPathSeparator+children[i], files);
			} else if (f.isFile()) {
				files.add(prefix+compatPathSeparator+children[i]);
			}
		}
	}
	public void loadIndexFromFolder(File packFolder) {
		//build the index from that folder
		ArrayList<String> files = new ArrayList<String>(8192);
		traverseAndAddToArray(packFolder, files);
		
		index = new ArrayList<MLPFileEntry>();
		long pos = compatFixedHeaderSize <= 0 ? 32 : compatFixedHeaderSize;
		for (int i = 0; i < files.size(); i++) {
			String path = files.get(i);
			File file = new File(packFolder, path);
			
			long start = pos;
			long size = file.length();
			pos += size;
			long end = pos;
			path = path.replace('\\', '/');
			path = path.substring(path.indexOf('/')+1);
			MLPFileEntry entry = new MLPFileEntry(start, end, size/32, start, path);
			index.add(entry);
		}
		indexOffset = pos;
	}
	public void recalculateIndex() {
		throw new UnsupportedOperationException("Not supported yet!");
	}
	/** Extracts a single file from the archive. **/
	public void unpackFile(MLPFileEntry entry, File destFolder) throws FileNotFoundException, IOException {
		prepareRead();
		
		File destFile = new File(destFolder, entry.path);
		if (!compatFullPaths && !isAncestorOf(destFile, destFolder)) {
			printlnerr(1, "Warning: \""+destFile.getPath()+"\" is not an ancestor of \""+destFolder+"\"! Skipping.");
			return;
		}
		destFile.getParentFile().mkdirs();
		
		OutputStream os = new FileOutputStream(destFile);
		//for all the data...
		archRead.seek(entry.startOffset);
		byte[] buffer = new byte[compatWriteBufferSize];
		int pos = 0;
		while (true) {
			long rem = entry.size()-pos;
			if (rem <= 0) break;
			int read = archRead.read(buffer, 0, rem < compatWriteBufferSize ? (int)rem : compatWriteBufferSize);
			if (read <= 0) break;
			os.write(buffer, 0, read);
			pos += read;
		}
		os.flush(); os.close();
	}
	public void unpackArchive(File destFolder) throws FileNotFoundException, IOException {
		NumberFormat format = NumberFormat.getPercentInstance(); format.setMinimumFractionDigits(1); format.setMaximumFractionDigits(1);
		for (int i = 0; i < index.size(); i++) {
			MLPFileEntry entry = index.get(i);
			printout(1, "Unpacking "+(i+1)+"/"+index.size()+" ("+format.format((float)(i+1)/index.size())+"): \""+entry.path+"\" ("+entry.size()+" bytes)...");
				unpackFile(entry, destFolder);
			if (verbosity >= 1) printlnout(1, "done.");
			else printout(0, ".");
		}
		if (verbosity <= 0) printlnout(0, "");
	}
	public void writeHeaderToArchive() throws FileNotFoundException, IOException {
		prepareWrite();
		
		int headerLength = compatFixedHeaderSize > 0 ? compatFixedHeaderSize : 32; //header length
		archWrite.seek(0);
		
		byte[] data = new byte[headerLength];
		
		//write false header
		for (int i = 0; i < headerLength; i++)
			data[i] = (byte)'0';
		
		CharsetEncoder encoder = indexCharset.newEncoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
		ByteBuffer bb = ByteBuffer.allocate(headerLength);
		
		bb.clear();
		encoder.reset();
		encoder.encode(CharBuffer.wrap(Long.toString(indexOffset, 10)), bb, true);
		encoder.flush(bb);
		bb.flip();
		
		bb.get(data, headerLength-bb.remaining(), bb.remaining());
		
		archWrite.write(data, 0, headerLength);
	}
	public void writeFilesToArchive(File packFolder, Pattern pat) throws FileNotFoundException, IOException {
		prepareWrite();
		NumberFormat format = NumberFormat.getPercentInstance(); format.setMinimumFractionDigits(1); format.setMaximumFractionDigits(1);
		
		//preallocate file space
		archWrite.setLength(indexOffset);
		
		int pos = compatFixedHeaderSize > 0 ? compatFixedHeaderSize : 32; //header length
		archWrite.seek(pos);
		
		byte[] buffer = new byte[compatWriteBufferSize];
		
		//write files
		for (int i = 0; i < index.size(); i++) {
			MLPFileEntry entry = index.get(i);
			if (pat != null && !pat.matcher(entry.path).matches())
				continue; //skipping
			//if (i > 10 && i < index.size()-10) { pos+=entry.size(); archWrite.seek(pos); continue; }
			printout(1, "Packing "+(i+1)+"/"+index.size()+" ("+format.format((float)(i+1)/index.size())+"): \""+entry.path+"\" ("+entry.size()+" bytes)...");
			
			File file = new File(packFolder, entry.path);
			if (!file.exists() || file.isDirectory())
				throw new IllegalStateException("Misplaced file: "+entry.path);
			if (file.length() != entry.size())
				throw new IllegalStateException("File size changed: "+entry.path);
			
			
			//okay should be good now.
			int rpos = 0;
			FileInputStream is = new FileInputStream(file);
			while (true) {
				long rem = entry.size()-rpos;
				if (rem <= 0) break;
				int read = is.read(buffer, 0, rem < compatWriteBufferSize ? (int)rem : compatWriteBufferSize);
				if (read <= 0) break;
				archWrite.write(buffer, 0, read);
				rpos += read;
			}
			pos += entry.size();
			if (verbosity >= 1) printlnout(1, "done.");
			else { if (i%100==0) { printout(0, "("+i+"/"+index.size()+")"); } else printout(0, "."); }
		}
		if (verbosity <= 0) printlnout(0, "");
		if (pos != indexOffset)
			throw new IllegalStateException("End of write doesn't meet index position!");
	}
	public void writeIndexToArchive() throws FileNotFoundException, IOException {
		prepareWrite();
		
		archWrite.seek(indexOffset);
		
		StringBuilder sb = new StringBuilder();
		CharsetEncoder encoder = indexCharset.newEncoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
		ByteBuffer bb = ByteBuffer.allocate(compatMaxLineLength);
		for (int i = 0; i < index.size(); i++) {
			MLPFileEntry entry = index.get(i);
			sb.delete(0, sb.length());
			
			sb.append(entry.startOffset);
			sb.append(compatFieldChar);
			sb.append(entry.endOffset);
			sb.append(compatFieldChar);
			sb.append(entry.magic0);
			sb.append(compatFieldChar);
			sb.append(entry.magic1);
			sb.append(compatFieldChar);
			sb.append(entry.path);
			sb.append(compatNewLineChar);
			
			bb.clear();
			encoder.reset();
			encoder.encode(CharBuffer.wrap(sb), bb, true);
			encoder.flush(bb);
			bb.flip();
			
			archWrite.write(bb.array(), 0, bb.remaining());
		}
	}
}
