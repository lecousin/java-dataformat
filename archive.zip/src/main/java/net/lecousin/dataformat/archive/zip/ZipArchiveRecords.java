package net.lecousin.dataformat.archive.zip;

import java.io.EOFException;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

import net.lecousin.dataformat.archive.zip.ZipArchive.ZippedFile;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.mutable.MutableLong;

class ZipArchiveRecords {
	
	public static final int CentralDirectoryID = 0x0102;
	public static final int LocalFileID = 0x0304;

	static int readCentralDirectory(IO.Readable.Buffered input, boolean firstEntryIDRead, MutableLong pos, Listener<ZippedFile> listener) throws IOException {
		if (!firstEntryIDRead) {
			if (input.read() != 'P') throw new IOException("Central Directory must start with PK0102");
			if (input.read() != 'K') throw new IOException("Central Directory must start with PK0102");
			if (input.read() != 0x01) throw new IOException("Central Directory must start with PK0102");
			if (input.read() != 0x02) throw new IOException("Central Directory must start with PK0102");
			if (pos != null) pos.add(4);
		}
		do {
			ZippedFile entry = readCentralDirectoryEntry(input, listener);
			if (pos != null)
				pos.add(entry.headerLength - 4);
			int c = input.read();
			if (c == -1) return -1;
			if (pos != null) pos.inc();
			if (c != 'P')
				throw new IOException("Character 0x"+Integer.toHexString(c)+" found after Central Directory entry, another record starting with PK must follow");
			c = input.read();
			if (c == -1) return -1;
			if (pos != null) pos.inc();
			if (c != 'K')
				throw new IOException("Character P then 0x"+Integer.toHexString(c)+" found after Central Directory entry, another record starting with PK must follow");
			int c1 = input.read();
			if (c1 == -1) return -1;
			if (pos != null) pos.inc();
			int c2 = input.read();
			if (c2 == -1) return -1;
			if (pos != null) pos.inc();
			int type = (c1<<8)|c2;
			if (type != CentralDirectoryID)
				return type;
		} while (true);
	}
	static ZippedFile readCentralDirectoryEntry(IO.Readable.Buffered input, Listener<ZippedFile> listener) throws IOException {
		ZippedFile info = new ZippedFile();
		info.versionMadeBy = DataUtil.readUnsignedShortLittleEndian(input);
		info.versionNeeded = DataUtil.readUnsignedShortLittleEndian(input);
		info.flags = DataUtil.readUnsignedShortLittleEndian(input);
		info.compressionMethod = DataUtil.readUnsignedShortLittleEndian(input);
		int mod_time = DataUtil.readUnsignedShortLittleEndian(input);
		int mod_date = DataUtil.readUnsignedShortLittleEndian(input);
		// mod_time and date may be null in case of encryption
		if (mod_time != 0 && mod_date != 0) {
			Calendar cal = Calendar.getInstance();
			cal.set(
				(mod_date&0xFE00)>>9 + 1980, 
				(mod_date&0x01E0)>>5 + 1, 
				(mod_date&0x001F), 
				(mod_time&0xF800)>>11, 
				(mod_time&0x07E0)>>5, 
				(mod_time&0x001F)
			);
			info.lastModificationTimestamp = cal.getTimeInMillis();
		} else
			info.lastModificationTimestamp = 0;
		info.crc32 = DataUtil.readUnsignedIntegerLittleEndian(input);
		info.compressedSize = DataUtil.readUnsignedIntegerLittleEndian(input);
		info.uncompressedSize = DataUtil.readUnsignedIntegerLittleEndian(input);
		int name_len = DataUtil.readUnsignedShortLittleEndian(input);
		int extra_len = DataUtil.readUnsignedShortLittleEndian(input);
		int comment_len = DataUtil.readUnsignedShortLittleEndian(input);
		info.diskNumberStart = DataUtil.readUnsignedShortLittleEndian(input);
		/* info.internalAttributes = */ DataUtil.readUnsignedShortLittleEndian(input);
		/* info.externalAttributes = */ DataUtil.readUnsignedIntegerLittleEndian(input);
		info.offset = DataUtil.readUnsignedIntegerLittleEndian(input);
		if (name_len > 0) {
			byte[] buf = new byte[name_len];
			int i = input.readFully(buf);
			if (i < name_len) throw new EOFException();
			info.filename = (info.flags&0x800) != 0 ? new String(buf, "UTF-8") : new String(buf);
		}
		if (extra_len > 0)
			readExtraFields(info, extra_len, input);
		if (comment_len > 0) {
			byte[] buf = new byte[comment_len];
			int i = input.readFully(buf);
			if (i < comment_len) throw new EOFException();
			info.comment = (info.flags&0x800) != 0 ? new String(buf, "UTF-8") : new String(buf);
		}
		info.headerLength = 4 + 42 + name_len + extra_len + comment_len;
		listener.fire(info);
		return info;
	}
	static ZippedFile readLocalFileEntry(IO.Readable.Buffered input, boolean headerRead, long offset) throws IOException {
		if (!headerRead) {
			if (input.read() != 'P') throw new IOException("Invalid Local File entry: must start with PK0304");
			if (input.read() != 'K') throw new IOException("Invalid Local File entry: must start with PK0304");
			if (input.read() != 0x03) throw new IOException("Invalid Local File entry: must start with PK0304");
			if (input.read() != 0x04) throw new IOException("Invalid Local File entry: must start with PK0304");
		}
		ZippedFile info = new ZippedFile();
		info.localEntry = info;
		info.offset = offset;
		info.versionNeeded = DataUtil.readUnsignedShortLittleEndian(input);
		info.flags = DataUtil.readUnsignedShortLittleEndian(input);
		info.compressionMethod = DataUtil.readUnsignedShortLittleEndian(input);
		int mod_time = DataUtil.readUnsignedShortLittleEndian(input);
		int mod_date = DataUtil.readUnsignedShortLittleEndian(input);
		// mod_time and date may be null in case of encryption
		if (mod_time != 0 && mod_date != 0) {
			Calendar cal = Calendar.getInstance();
			cal.set(
				(mod_date&0xFE00)>>9 + 1980, 
				(mod_date&0x01E0)>>5 + 1, 
				(mod_date&0x001F), 
				(mod_time&0xF800)>>11, 
				(mod_time&0x07E0)>>5, 
				(mod_time&0x001F)
			);
			info.lastModificationTimestamp = cal.getTimeInMillis();
		} else
			info.lastModificationTimestamp = 0;
		info.crc32 = DataUtil.readUnsignedIntegerLittleEndian(input);
		info.compressedSize = DataUtil.readUnsignedIntegerLittleEndian(input);
		info.uncompressedSize = DataUtil.readUnsignedIntegerLittleEndian(input);
		int name_len = DataUtil.readUnsignedShortLittleEndian(input);
		int extra_len = DataUtil.readUnsignedShortLittleEndian(input);
		if (name_len > 0) {
			byte[] buf = new byte[name_len];
			int i = input.readFully(buf);
			if (i < name_len) throw new EOFException();
			info.filename = (info.flags&0x800) != 0 ? new String(buf, "UTF-8") : new String(buf);
		}
		if (extra_len > 0)
			readExtraFields(info, extra_len, input);
		info.headerLength = 4 + 26 + name_len + extra_len;
		return info;
	}
	
	static void readExtraFields(ZippedFile info, int len, IO.Readable.Buffered input) throws IOException {
		do {
			if (len < 4) {
				// invalid, just skip it
				input.skip(len);
				return;
			}
			int extra_id = DataUtil.readUnsignedShortLittleEndian(input);
			int extra_len = DataUtil.readUnsignedShortLittleEndian(input);
			if (extra_len > len-4) {
				// invalid, just skip remaining bytes
				input.skip(len);
				return;
			}
			switch (extra_id) {
			case 0x0001: readExtraFieldZip64(info, extra_len, input); break;
			//TODO case 0x0007: readExtraFieldAVInfo(header, extra_len); break;
			//TODO case 0x0008: readExtraFieldLanguageExtendingData(header, extra_len); break;
			//TODO case 0x0009: readExtraFieldOS2(header, extra_len); break;
			case 0x000A: readExtraFieldNTFS(info, extra_len, input); break;
			//TODO case 0x000C: readExtraFieldOpenVMS(header, extra_len); break;
			case 0x000D: readExtraFieldUNIX(info, extra_len, input); break;
			//TODO case 0x000E: readExtraFieldFileStream(header, extra_len); break;
			//TODO case 0x000F: readExtraFieldPatchDescriptor(header, extra_len); break;
			//TODO case 0x0014: readExtraFieldPKCS7ForX509Certificates(header, extra_len); break;
			//TODO case 0x0015: readExtraFieldX509CertificateIDForFile(header, extra_len); break;
			//TODO case 0x0016: readExtraFieldX509CertificateIDForCentralDirectory(header, extra_len); break;
			//TODO case 0x0017: readExtraFieldStrongEncryption(header, extra_len); break;
			//TODO case 0x0018: readExtraFieldRecordManagementControls(header, extra_len); break;
			//TODO case 0x0019: readExtraFieldPKCS7EcnryptionRecipientCertificateList(header, extra_len); break;
			//TODO case 0x0065: readExtraFieldS390_AS400_uncompressed(header, extra_len); break;
			//TODO case 0x0066: readExtraFieldS390_AS400_compressed(header, extra_len); break;
			//TODO case 0x4690: readExtraFieldPOSZIP4690(header, extra_len); break;
			// third-party
			case 0x5455: readExtraFieldExtendedTimestamp(info, extra_len, input); break;
			case 0x5855: readExtraFieldUnix1(info, extra_len, input); break;
			case 0x7855: readExtraFieldUnix2(info, extra_len, input); break;
			case 0x7875: readExtraFieldUnixN(/*info, */extra_len, input); break;
			case 0xA220: // this is used as growth hint by office open XML documents
				// TODO mark as Office Open XML
				input.skip(extra_len);
				break;
			case 0xCAFE: // this is used by Java
				// TODO mark as Java Executable
				input.skip(extra_len);
				break;
			default:
				if (ZipArchive.logger.isInfoEnabled()) ZipArchive.logger.info("ZipArchive: Unknown extra field ID 0x"+Integer.toHexString(extra_id)+" in "+input.getSourceDescription());
				input.skip(extra_len);
				break;
			}
			/*
			 * http://www.opensource.apple.com/source/zip/zip-6/unzip/unzip/proginfo/extra.fld
			 * https://github.com/LuaDist/zip/blob/master/proginfo/extrafld.txt
			Third party mappings commonly used are:

          0x07c8        Macintosh
          0x2605        ZipIt Macintosh
          0x2705        ZipIt Macintosh 1.3.5+
          0x2805        ZipIt Macintosh 1.3.5+
          0x334d        Info-ZIP Macintosh
          0x4341        Acorn/SparkFS 
          0x4453        Windows NT security descriptor (binary ACL)
          0x4704        VM/CMS
          0x470f        MVS
          0x4b46        FWKCS MD5 (see below)
          0x4c41        OS/2 access control list (text ACL)
          0x4d49        Info-ZIP OpenVMS
          0x4f4c        Xceed original location extra field
          0x5356        AOS/VS (ACL)
          *0x5455        extended timestamp
          0x554e        Xceed unicode extra field
          *0x5855        Info-ZIP UNIX (original, also OS/2, NT, etc)
          0x6375        Info-ZIP Unicode Comment Extra Field
          0x6542        BeOS/BeBox
          0x7075        Info-ZIP Unicode Path Extra Field
          0x756e        ASi UNIX
          *0x7855        Info-ZIP UNIX (new)
          *0xa220        Microsoft Open Packaging Growth Hint
          0xfd4a        SMS/QDOS			 * 
			 */
			len -= 4 + extra_len;
		} while (len > 0);
	}
	static void readExtraFieldZip64(ZippedFile header, int len, IO.Readable.Buffered input) throws IOException {
		if (len != 28) {
			if (ZipArchive.logger.isInfoEnabled()) ZipArchive.logger.info("ZipArchive: Invalid Zip64 Extra field: expected length is 28, found is "+len);
			input.skip(len);
			return;
		}
		if (header.uncompressedSize == 0xFFFFFFFF)
			header.uncompressedSize = DataUtil.readLongLittleEndian(input);
		else
			input.skip(8);
		if (header.compressedSize == 0xFFFFFFFF)
			header.compressedSize = DataUtil.readLongLittleEndian(input);
		else
			input.skip(8);
		if (header.offset == 0xFFFFFFFF)
			header.offset = DataUtil.readLongLittleEndian(input);
		else
			input.skip(8);
		if (header.diskNumberStart == 0xFFFF)
			header.diskNumberStart = DataUtil.readUnsignedIntegerLittleEndian(input);
		else
			input.skip(4);
	}
	static void readExtraFieldNTFS(ZippedFile header, int len, IO.Readable.Buffered input) throws IOException {
		// 4 bytes reserved
		input.skip(4);
		len -= 4;
		while (len > 0) {
			int type = DataUtil.readUnsignedShortLittleEndian(input);
			int size = DataUtil.readUnsignedShortLittleEndian(input);
			len -= 4;
			switch (type) {
			case 0x0001:
				if (size != 3*8) {
					LCCore.getApplication().getDefaultLogger().error("Unexpected size for NTFS attribute 1: found "+size+" exepected is 24: "+input.getSourceDescription());
					input.skip(size);
					len -= size;
					break;
				}
				/*
				 The NTFS filetimes are 64-bit unsigned integers, stored in Intel
		          (least significant byte first) byte order. They determine the
		          number of 1.0E-07 seconds (1/10th microseconds!) past WinNT "epoch",
		          which is "01-Jan-1601 00:00:00 UTC"
				 */
				Calendar cal = Calendar.getInstance();
				cal.clear();
				cal.setTimeZone(TimeZone.getTimeZone("GMT"));
				cal.set(1601, 0, 1, 0, 0, 0);
				long diff = cal.getTimeInMillis();
				header.lastModificationTimestamp = DataUtil.readLongLittleEndian(input)/10000+diff;
				header.lastAccessTimestamp = DataUtil.readLongLittleEndian(input)/10000+diff;
				header.creationTimestamp = DataUtil.readLongLittleEndian(input)/10000+diff;
				len -= 8*3;
				break;
			default:
				LCCore.getApplication().getDefaultLogger().warn("Unknown NTFS attribute "+type);
				input.skip(size);
				len -= size;
				break;
			}
		}
	}
	static void readExtraFieldUNIX(ZippedFile header, int len, IO.Readable.Buffered input) throws IOException {
		if (len < 12) {
			LCCore.getApplication().getDefaultLogger().error("Unexpected size of UNIX info: found "+len+", expected is at least 12: "+input.getSourceDescription());
			input.skip(len);
			return;
		}
		header.lastAccessTimestamp = DataUtil.readUnsignedIntegerLittleEndian(input)*1000;
		header.lastModificationTimestamp = DataUtil.readUnsignedIntegerLittleEndian(input)*1000;
		header.userID = DataUtil.readUnsignedShortLittleEndian(input);
		header.groupID = DataUtil.readUnsignedShortLittleEndian(input);
		if (len > 12)
			input.skip(len-12);
	}
	static void readExtraFieldUnix1(ZippedFile header, int len, IO.Readable.Buffered input) throws IOException {
		if (len < 8) {
			LCCore.getApplication().getDefaultLogger().error("Unexpected size of Unix1 info: found "+len+", expected is at least 8: "+input.getSourceDescription());
			input.skip(len);
			return;
		}
		header.lastAccessTimestamp = DataUtil.readUnsignedIntegerLittleEndian(input)*1000;
		header.lastModificationTimestamp = DataUtil.readUnsignedIntegerLittleEndian(input)*1000;
		if (len > 8) {
			header.userID = DataUtil.readUnsignedShortLittleEndian(input);
			header.groupID = DataUtil.readUnsignedShortLittleEndian(input);
		}
	}
	static void readExtraFieldUnix2(ZippedFile header, int len, IO.Readable.Buffered input) throws IOException {
		if (len > 0) {
			header.userID = DataUtil.readUnsignedShortLittleEndian(input);
			header.groupID = DataUtil.readUnsignedShortLittleEndian(input);
		}
	}
	static void readExtraFieldUnixN(/*ZippedFile header, */int len, IO.Readable.Buffered input) throws IOException {
		input.skip(len);
		/*
Value         Size        Description
 -----         ----        -----------
 0x7875        Short       tag for this extra block type ("ux")
 TSize         Short       total data size for this block
 Version       1 byte      version of this extra field, currently 1
 UIDSize       1 byte      Size of UID field
 UID           Variable    UID for this entry (little endian)
 GIDSize       1 byte      Size of GID field
 GID           Variable    GID for this entry (little endian)
		 */
	}
	static void readExtraFieldExtendedTimestamp(ZippedFile header, int len, IO.Readable.Buffered input) throws IOException {
		int bits = input.read();
		len--;
		if (len > 0 && (bits & 1) != 0) {
			header.lastModificationTimestamp = DataUtil.readUnsignedIntegerLittleEndian(input)*1000;
			len -= 4; 
		}
		if (len > 0 && (bits & 2) != 0) {
			header.lastAccessTimestamp = DataUtil.readUnsignedIntegerLittleEndian(input)*1000;
			len -= 4; 
		}
		if (len > 0 && (bits & 4) != 0) {
			header.creationTimestamp = DataUtil.readUnsignedIntegerLittleEndian(input)*1000;
			len -= 4; 
		}
		if (len > 0) input.skip(len);
	}
	
	static class EndOfCentralDirectory {
		static final int ID = 0x0506;
		static final int ZIP64_ID = 0x0606;
		
		long numOfThisDisk;
		long diskWithStartOfCentralDirectory;
		long nbCentralDirectoryEntriesInThisDisk;
		long nbCentralDirectoryEntries;
		long centralDirectorySize;
		long centralDirectoryOffset;
		String comment;
		int headerLength;
		
		boolean needsZip64() {
			return 
				(numOfThisDisk == 0xFFFF) ||
				(diskWithStartOfCentralDirectory == 0xFFFF) ||
				(nbCentralDirectoryEntriesInThisDisk == 0xFFFF) ||
				(nbCentralDirectoryEntries == 0xFFFF) ||
				(centralDirectorySize == 0xFFFFFFFFL) ||
				(centralDirectoryOffset == 0xFFFFFFFFL);
		}
		
		public static EndOfCentralDirectory read(IO.ReadableByteStream io) throws IOException {
			EndOfCentralDirectory record = new EndOfCentralDirectory();
			record.numOfThisDisk = DataUtil.readUnsignedShortLittleEndian(io);
			record.diskWithStartOfCentralDirectory = DataUtil.readUnsignedShortLittleEndian(io);
			record.nbCentralDirectoryEntriesInThisDisk = DataUtil.readUnsignedShortLittleEndian(io);
			record.nbCentralDirectoryEntries = DataUtil.readUnsignedShortLittleEndian(io);
			record.centralDirectorySize = DataUtil.readUnsignedIntegerLittleEndian(io);
			record.centralDirectoryOffset = DataUtil.readUnsignedIntegerLittleEndian(io);
			int comment_len = DataUtil.readUnsignedShortLittleEndian(io);
			if (comment_len > 0) {
				byte[] buf = new byte[comment_len];
				for (int pos = 0; pos < comment_len; ++pos) {
					int c = io.read();
					if (c < 0) throw new EOFException();
					buf[pos] = (byte)c;
				}
				record.comment = new String(buf);
			} else
				record.comment = "";
			record.headerLength = 18 + comment_len;
			return record;
		}
		
		public static long readZip64(IO.Readable.Buffered io, EndOfCentralDirectory record) throws IOException {
			long size = DataUtil.readLongLittleEndian(io);
			/* version made by */ DataUtil.readUnsignedShortLittleEndian(io);
			/* version needed */ DataUtil.readUnsignedShortLittleEndian(io);
			record.numOfThisDisk = DataUtil.readUnsignedIntegerLittleEndian(io);
			record.diskWithStartOfCentralDirectory = DataUtil.readUnsignedIntegerLittleEndian(io);
			record.nbCentralDirectoryEntriesInThisDisk = DataUtil.readLongLittleEndian(io);
			record.nbCentralDirectoryEntries = DataUtil.readLongLittleEndian(io);
			record.centralDirectorySize = DataUtil.readLongLittleEndian(io);
			record.centralDirectoryOffset = DataUtil.readLongLittleEndian(io);
			size -= 44;
			// TODO read extensible data ?
			io.skipSync(size);
			return size + 44 + 8;
		}
	}
	
	static class Zip64EndOfCentralDirectoryLocator {
		public static final int ID = 0x0607;
		
		long numDiskWithStartOfZip64EndCentralDirectory;
		long relativeOffsetOfZip64EndOfCentralDirectory;
		long totalNbDisks;
		
		public static Zip64EndOfCentralDirectoryLocator read(IO.ReadableByteStream io) throws IOException {
			Zip64EndOfCentralDirectoryLocator record = new Zip64EndOfCentralDirectoryLocator();
			record.numDiskWithStartOfZip64EndCentralDirectory = DataUtil.readUnsignedIntegerLittleEndian(io);
			record.relativeOffsetOfZip64EndOfCentralDirectory = DataUtil.readLongLittleEndian(io);
			record.totalNbDisks = DataUtil.readUnsignedIntegerLittleEndian(io);
			return record;
		}
	}
	/*
	private static byte[] PK = new byte[] { 'P', 'K' };
	
	static byte[] prepareLocalFileEntry(String filename, long compressedSize, long uncompressedSize, long crc32) {
		// determine if the filename should be encoded in UTF8 or not
		boolean isAscii = true;
		int fn_len = filename.length();
		for (int i = 0; i < fn_len; ++i) {
			char c = filename.charAt(i);
			if (c < 0x20 || c >= 0x7F) {
				isAscii = false;
				break;
			}
		}
		byte[] fn = isAscii ? filename.getBytes("US-ASCII") : filename.getBytes("UTF-8");
		byte[] buf = new byte[30+fn.length];
		buf[0] = 'P';
		buf[1] = 'K';
		buf[2] = 3;
		buf[3] = 4;
		buf[4] = 10; // TODO version needed to extract
		buf[5] = 0;
		buf[6] = 0; // TODO flags
		buf[7] = (byte)(0 | (isAscii ? 0 : 0x08));
		buf[8] = 8; // TODO compression method
		buf[9] = 0;
		// TODO 10-13 = time and date
		DataUtil.writeUnsignedIntegerIntel(buf, 14, crc32);
		DataUtil.writeUnsignedIntegerIntel(buf, 18, compressedSize >= 0xFFFFFFFFL ? 0xFFFFFFFFL : compressedSize);
		DataUtil.writeUnsignedIntegerIntel(buf, 22, uncompressedSize >= 0xFFFFFFFFL ? 0xFFFFFFFFL : uncompressedSize);
		DataUtil.writeUnsignedShortIntel(buf, 26, fn.length);
		// 28-29 = extra fields length
		System.arraycopy(fn, 0, buf, 30, fn.length);
		return buf;
	}
	*/
}
