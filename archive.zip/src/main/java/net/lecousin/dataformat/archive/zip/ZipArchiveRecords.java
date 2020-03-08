package net.lecousin.dataformat.archive.zip;

import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.function.Consumer;

import net.lecousin.dataformat.archive.zip.ZipArchive.ZippedFile;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.log.Logger;

class ZipArchiveRecords {
	
	public static final int CentralDirectoryID = 0x0102;
	public static final int LocalFileID = 0x0304;

	static Async<IOException> readCentralDirectory(IO.Readable.Buffered input, Consumer<ZippedFile> listener) {
		Async<IOException> sp = new Async<>();
		byte[] pk = new byte[4];
		readNextCentralDirectoryEntry(input, listener, pk, true, sp, 0);
		return sp;
	}
	
	private static void readNextCentralDirectoryEntry(IO.Readable.Buffered input, Consumer<ZippedFile> listener, byte[] pk, boolean firstEntry, Async<IOException> sp, int recursCount) {
		AsyncSupplier<Integer, IOException> r = input.readFullySyncIfPossible(ByteBuffer.wrap(pk));
		if (!r.isDone()) {
			r.thenStart("Read ZIP central directory", input.getPriority(), () -> {
				readCentralDirectoryEntry(input, listener, pk, firstEntry, sp, r.getResult().intValue(), 0);
			}, sp);
			return;
		}
		if (r.hasError()) {
			sp.error(r.getError());
			return;
		}
		if (r.isCancelled()) {
			sp.cancel(r.getCancelEvent());
			return;
		}
		readCentralDirectoryEntry(input, listener, pk, firstEntry, sp, r.getResult().intValue(), recursCount + 1);
	}
	
	private static void readCentralDirectoryEntry(IO.Readable.Buffered input, Consumer<ZippedFile> listener, byte[] pk, boolean firstEntry, Async<IOException> sp, int nbRead, int recursCount) {
		if (nbRead != 4) {
			if (firstEntry) {
				sp.error(new IOException("Unexpected end of file at the beginning of ZIP central directory"));
				return;
			}
			sp.unblock();
			return;
		}
		if (pk[0] != 'P' || pk[1] != 'K' || pk[2] != 1 || pk[3] != 2) {
			if (firstEntry) {
				sp.error(new IOException("Central Directory must start with PK0102"));
				return;
			}
			sp.unblock();
			return;
		}
		AsyncSupplier<ZippedFile, IOException> r = readCentralDirectoryEntry(input);
		if (!r.isDone()) {
			r.thenStart("Read ZIP central directory", input.getPriority(), () -> {
				centralDirectoryEntryRead(input, listener, pk, sp, r.getResult(), 0);
			}, sp);
			return;
		}
		if (r.hasError()) {
			sp.error(r.getError());
			return;
		}
		if (r.isCancelled()) {
			sp.cancel(r.getCancelEvent());
			return;
		}
		centralDirectoryEntryRead(input, listener, pk, sp, r.getResult(), recursCount + 1);
	}
	
	private static void centralDirectoryEntryRead(IO.Readable.Buffered input, Consumer<ZippedFile> listener, byte[] pk, Async<IOException> sp, ZippedFile entry, int recursCount) {
		if (listener != null)
			listener.accept(entry);
		if (recursCount < 100)
			readNextCentralDirectoryEntry(input, listener, pk, false, sp, recursCount + 1);
		else
			Task.cpu("Read ZIP central directory", input.getPriority(), t -> {
				readNextCentralDirectoryEntry(input, listener, pk, false, sp, 0);
				return null;
			}).start();
	}
	
	static AsyncSupplier<ZippedFile, IOException> readCentralDirectoryEntry(IO.Readable.Buffered input) {
		byte[] buf = new byte[42];
		AsyncSupplier<ZippedFile, IOException> result = new AsyncSupplier<>();
		AsyncSupplier<Integer, IOException> read = input.readFullySyncIfPossible(ByteBuffer.wrap(buf));
		if (!read.isDone()) {
			read.thenStart("Read ZIP central directory entry", input.getPriority(), () -> {
				if (read.getResult().intValue() != 42)
					result.error(new EOFException("Unexpected end of file in a middle of a central directory entry"));
				else
					readCentralDirectoryEntry(input, buf, result);
			}, result);
			return result;
		}
		if (read.getResult().intValue() != 42)
			result.error(new EOFException("Unexpected end of file in a middle of a central directory entry"));
		else
			readCentralDirectoryEntry(input, buf, result);
		return result;
	}

	private static void readCentralDirectoryEntry(IO.Readable.Buffered input, byte[] buf, AsyncSupplier<ZippedFile, IOException> result) {
		ZippedFile info = new ZippedFile();
		info.versionMadeBy = DataUtil.Read16U.LE.read(buf, 0);
		info.versionNeeded = DataUtil.Read16U.LE.read(buf, 2);
		info.flags = DataUtil.Read16U.LE.read(buf, 4);
		info.compressionMethod = DataUtil.Read16U.LE.read(buf, 6);
		int mod_time = DataUtil.Read16U.LE.read(buf, 8);
		int mod_date = DataUtil.Read16U.LE.read(buf, 10);
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
		info.crc32 = DataUtil.Read32U.LE.read(buf, 12);
		info.compressedSize = DataUtil.Read32U.LE.read(buf, 16);
		info.uncompressedSize = DataUtil.Read32U.LE.read(buf, 20);
		int name_len = DataUtil.Read16U.LE.read(buf, 24);
		int extra_len = DataUtil.Read16U.LE.read(buf, 26);
		int comment_len = DataUtil.Read16U.LE.read(buf, 28);
		info.diskNumberStart = DataUtil.Read16U.LE.read(buf, 30);
		/* info.internalAttributes = DataUtil.Read16U.LE.read(buf, 32);*/
		/* info.externalAttributes = DataUtil.Read32U.LE.read(buf, 34);*/
		info.offset = DataUtil.Read32U.LE.read(buf, 38);
		info.headerLength = 4 + 42 + name_len + extra_len + comment_len;
		if (name_len > 0)
			readEntryName(info, name_len, extra_len, 0, input, result);
		else if (extra_len > 0)
			readExtraFields(info, input, extra_len, 0, result);
		else
			result.unblockSuccess(info);
	}
	
	static AsyncSupplier<ZippedFile, IOException> readLocalFileEntry(IO.Readable.Buffered input, boolean headerRead, long offset) {
		byte[] buf = new byte[26];
		AsyncSupplier<ZippedFile, IOException> result = new AsyncSupplier<>();
		if (!headerRead) {
			AsyncSupplier<Integer, IOException> r = input.readFullySyncIfPossible(ByteBuffer.wrap(buf, 0, 4));
			if (!r.isDone()) {
				r.thenStart("Read ZIP Local entry", input.getPriority(), () -> {
					readLocalFileEntryHeader(input, offset, buf, result);
				}, result);
				return result;
			}
			readLocalFileEntryHeader(input, offset, buf, result);
			return result;
		}
		readLocalFileEntryBuffer(input, offset, buf, result);
		return result;
	}
	
	private static void readLocalFileEntryHeader(IO.Readable.Buffered input, long offset, byte[] buf, AsyncSupplier<ZippedFile, IOException> result) {
		if (buf[0] != 'P' || buf[1] != 'K' || buf[2] != 0x03 || buf[3] != 0x04) {
			result.error(new IOException("Invalid Local File entry at " + offset + ": must start with PK0304"));
			return;
		}
		readLocalFileEntryBuffer(input, offset, buf, result);
	}
	
	private static void readLocalFileEntryBuffer(IO.Readable.Buffered input, long offset, byte[] buf, AsyncSupplier<ZippedFile, IOException> result) {
		AsyncSupplier<Integer, IOException> read = input.readFullySyncIfPossible(ByteBuffer.wrap(buf));
		if (!read.isDone()) {
			read.thenStart("Read ZIP local file entry", input.getPriority(), () -> {
				if (read.getResult().intValue() != 26)
					result.error(new EOFException("Unexpected end of file in a middle of a local file entry"));
				else
					readLocalFileEntry(input, offset, buf, result);
			}, result);
			return;
		}
		if (read.getResult().intValue() != 26)
			result.error(new EOFException("Unexpected end of file in a middle of a local file entry"));
		else
			readLocalFileEntry(input, offset, buf, result);
	}
	
	private static void readLocalFileEntry(IO.Readable.Buffered input, long offset, byte[] buf, AsyncSupplier<ZippedFile, IOException> result) {
		ZippedFile info = new ZippedFile();
		info.localEntry = info;
		info.offset = offset;
		info.versionNeeded = DataUtil.Read16U.LE.read(buf, 0);
		info.flags = DataUtil.Read16U.LE.read(buf, 2);
		info.compressionMethod = DataUtil.Read16U.LE.read(buf, 4);
		int mod_time = DataUtil.Read16U.LE.read(buf, 6);
		int mod_date = DataUtil.Read16U.LE.read(buf, 8);
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
		info.crc32 = DataUtil.Read32U.LE.read(buf, 10);
		info.compressedSize = DataUtil.Read32U.LE.read(buf, 14);
		info.uncompressedSize = DataUtil.Read32U.LE.read(buf, 18);
		int name_len = DataUtil.Read16U.LE.read(buf, 22);
		int extra_len = DataUtil.Read16U.LE.read(buf, 24);
		info.headerLength = 4 + 26 + name_len + extra_len;
		if (name_len > 0)
			readEntryName(info, name_len, extra_len, 0, input, result);
		else if (extra_len > 0)
			readExtraFields(info, input, extra_len, 0, result);
		else
			result.unblockSuccess(info);
	}
	
	private static void readEntryName(ZippedFile info, int name_len, int extra_len, int comment_len, IO.Readable.Buffered input, AsyncSupplier<ZippedFile, IOException> result) {
		byte[] b = new byte[name_len];
		AsyncSupplier<Integer, IOException> r = input.readFullySyncIfPossible(ByteBuffer.wrap(b));
		if (r.isDone()) {
			if (!r.isSuccessful()) {
				if (r.hasError()) result.error(r.getError());
				else result.cancel(r.getCancelEvent());
				return;
			}
			readEntryName(info, name_len, r.getResult().intValue(), b, extra_len, comment_len, input, result);
			return;
		}
		r.thenStart("Read ZIP local file entry", input.getPriority(), () -> {
			readEntryName(info, name_len, r.getResult().intValue(), b, extra_len, comment_len, input, result);
		}, result);
	}
	
	private static void readEntryName(ZippedFile info, int name_len, int nbRead, byte[] b, int extra_len, int comment_len, IO.Readable.Buffered input, AsyncSupplier<ZippedFile, IOException> result) {
		if (nbRead < name_len) {
			result.error(new EOFException("Only " + nbRead + " bytes read on " + name_len + " for zip entry name"));
			return;
		}
		try {
			info.filename = (info.flags&0x800) != 0 ? new String(b, "UTF-8") : new String(b);
		} catch (UnsupportedEncodingException e) {
			// not possible
		}
		if (extra_len > 0)
			readExtraFields(info, input, extra_len, comment_len, result);
		else if (comment_len > 0)
			readComment(info, comment_len, input, result);
		else
			result.unblockSuccess(info);
	}
	
	private static void readComment(ZippedFile info, int comment_len, IO.Readable.Buffered input, AsyncSupplier<ZippedFile, IOException> result) {
		byte[] b = new byte[comment_len];
		AsyncSupplier<Integer, IOException> r = input.readFullySyncIfPossible(ByteBuffer.wrap(b));
		if (r.isDone()) {
			if (!r.isSuccessful()) {
				if (r.hasError()) result.error(r.getError());
				else result.cancel(r.getCancelEvent());
				return;
			}
			readComment(info, comment_len, r.getResult().intValue(), b, result);
			return;
		}
		r.thenStart("Read ZIP local file entry", input.getPriority(), () -> {
			readComment(info, comment_len, r.getResult().intValue(), b, result);
		}, result);
	}
	
	private static void readComment(ZippedFile info, int comment_len, int nbRead, byte[] b, AsyncSupplier<ZippedFile, IOException> result) {
		if (nbRead < comment_len) {
			result.error(new EOFException());
			return;
		}
		try {
			info.comment = (info.flags&0x800) != 0 ? new String(b, "UTF-8") : new String(b);
		} catch (UnsupportedEncodingException e) {
			// not possible
		}
		result.unblockSuccess(info);
	}
	
	private static void readExtraFields(ZippedFile info, IO.Readable.Buffered input, int len, int comment_len, AsyncSupplier<ZippedFile, IOException> result) {
		byte[] buf = new byte[len];
		AsyncSupplier<Integer, IOException> r = input.readFullySyncIfPossible(ByteBuffer.wrap(buf));
		if (r.isDone()) {
			if (!r.isSuccessful()) {
				if (r.hasError()) result.error(r.getError());
				else result.cancel(r.getCancelEvent());
				return;
			}
			readExtraFields(info, len, r.getResult().intValue(), buf, comment_len, input, result);
			return;
		}
		r.thenStart("Read ZIP local file entry", input.getPriority(), () -> {
			readExtraFields(info, len, r.getResult().intValue(), buf, comment_len, input, result);
		}, result);
	}

	private static void readExtraFields(ZippedFile info, int len, int nbRead, byte[] buf, int comment_len, IO.Readable.Buffered input, AsyncSupplier<ZippedFile, IOException> result) {
		if (nbRead < len) {
			result.unblockError(new EOFException());
			return;
		}
		int pos = 0;
		while (pos < len) {
			if (len - pos < 4) {
				// invalid, just skip it
				break;
			}
			int extra_id = DataUtil.Read16U.LE.read(buf, pos);
			int extra_len = DataUtil.Read16U.LE.read(buf, pos + 2);
			if (extra_len > len - pos - 4) {
				// invalid, just skip remaining bytes
				break;
			}
			switch (extra_id) {
			case 0x0001: readExtraFieldZip64(info, buf, pos + 4, extra_len); break;
			//TODO case 0x0007: readExtraFieldAVInfo(header, extra_len); break;
			//TODO case 0x0008: readExtraFieldLanguageExtendingData(header, extra_len); break;
			//TODO case 0x0009: readExtraFieldOS2(header, extra_len); break;
			case 0x000A: readExtraFieldNTFS(info, buf, pos + 4, extra_len); break;
			//TODO case 0x000C: readExtraFieldOpenVMS(header, extra_len); break;
			case 0x000D: readExtraFieldUNIX(info, buf, pos + 4, extra_len); break;
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
			case 0x5455: readExtraFieldExtendedTimestamp(info, buf, pos + 4, extra_len); break;
			case 0x5855: readExtraFieldUnix1(info, buf, pos + 4, extra_len); break;
			case 0x7855: readExtraFieldUnix2(info, buf, pos + 4, extra_len); break;
			case 0x7875:
				/* Unix N
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
				break;
			case 0xA220: // this is used as growth hint by office open XML documents
				// TODO mark as Office Open XML
				break;
			case 0xCAFE: // this is used by Java
				// TODO mark as Java Executable
				break;
			default:
				Logger logger = ZipArchive.getLogger();
				if (logger.info()) logger.info("ZipArchive: Unknown extra field ID 0x"+Integer.toHexString(extra_id)+" in "+input.getSourceDescription());
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
		
			pos += 4 + extra_len;
		}
		if (comment_len > 0)
			readComment(info, comment_len, input, result);
		else
			result.unblockSuccess(info);
	}
	
	private static void readExtraFieldZip64(ZippedFile header, byte[] buf, int pos, int len) {
		if (len != 28) {
			Logger logger = ZipArchive.getLogger();
			if (logger.info()) logger.info("ZipArchive: Invalid Zip64 Extra field: expected length is 28, found is "+len);
			return;
		}
		if (header.uncompressedSize == 0xFFFFFFFF)
			header.uncompressedSize = DataUtil.Read64.LE.read(buf, pos);
		if (header.compressedSize == 0xFFFFFFFF)
			header.compressedSize = DataUtil.Read64.LE.read(buf, pos + 8);
		if (header.offset == 0xFFFFFFFF)
			header.offset = DataUtil.Read64.LE.read(buf, pos + 16);
		if (header.diskNumberStart == 0xFFFF)
			header.diskNumberStart = DataUtil.Read32U.LE.read(buf, pos + 24);
	}
	
	static void readExtraFieldNTFS(ZippedFile header, byte[] buf, int pos, int len) {
		// 4 bytes reserved
		pos += 4;
		len -= 4;
		while (len > 0) {
			int type = DataUtil.Read16U.LE.read(buf, pos);
			int size = DataUtil.Read16U.LE.read(buf, pos + 2);
			pos += 4;
			len -= 4;
			switch (type) {
			case 0x0001:
				if (size != 3*8) {
					LCCore.getApplication().getDefaultLogger().error("Unexpected size for NTFS attribute 1: found "+size+" exepected is 24");
					pos += size;
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
				header.lastModificationTimestamp = DataUtil.Read64.LE.read(buf, pos)/10000+diff;
				header.lastAccessTimestamp = DataUtil.Read64.LE.read(buf, pos + 8)/10000+diff;
				header.creationTimestamp = DataUtil.Read64.LE.read(buf, pos + 16)/10000+diff;
				pos += 24;
				len -= 24;
				break;
			default:
				LCCore.getApplication().getDefaultLogger().warn("Unknown NTFS attribute "+type);
				pos += size;
				len -= size;
				break;
			}
		}
	}
	
	static void readExtraFieldUNIX(ZippedFile header, byte[] buf, int pos, int len) {
		if (len < 12) {
			LCCore.getApplication().getDefaultLogger().error("Unexpected size of UNIX info: found "+len+", expected is at least 12");
			return;
		}
		header.lastAccessTimestamp = DataUtil.Read32U.LE.read(buf, pos)*1000;
		header.lastModificationTimestamp = DataUtil.Read32U.LE.read(buf, pos + 4)*1000;
		header.userID = DataUtil.Read16U.LE.read(buf, pos + 8);
		header.groupID = DataUtil.Read16U.LE.read(buf, pos + 10);
	}
	
	static void readExtraFieldUnix1(ZippedFile header, byte[] buf, int pos, int len) {
		if (len < 8) {
			LCCore.getApplication().getDefaultLogger().error("Unexpected size of Unix1 info: found "+len+", expected is at least 8");
			return;
		}
		header.lastAccessTimestamp = DataUtil.Read32U.LE.read(buf, pos)*1000;
		header.lastModificationTimestamp = DataUtil.Read32U.LE.read(buf, pos + 4)*1000;
		if (len > 8) {
			header.userID = DataUtil.Read16U.LE.read(buf, pos + 8);
			header.groupID = DataUtil.Read16U.LE.read(buf, pos + 10);
		}
	}
	
	static void readExtraFieldUnix2(ZippedFile header, byte[] buf, int pos, int len) {
		if (len > 0) {
			header.userID = DataUtil.Read16U.LE.read(buf, pos);
			header.groupID = DataUtil.Read16U.LE.read(buf, pos + 2);
		}
	}
	
	static void readExtraFieldExtendedTimestamp(ZippedFile header, byte[] buf, int pos, int len) {
		byte bits = buf[pos];
		pos++;
		len--;
		if (len > 0 && (bits & 1) != 0) {
			header.lastModificationTimestamp = DataUtil.Read32U.LE.read(buf, pos)*1000;
			pos += 4;
			len -= 4; 
		}
		if (len > 0 && (bits & 2) != 0) {
			header.lastAccessTimestamp = DataUtil.Read32U.LE.read(buf, pos)*1000;
			pos += 4;
			len -= 4; 
		}
		if (len > 0 && (bits & 4) != 0) {
			header.creationTimestamp = DataUtil.Read32U.LE.read(buf, pos)*1000;
			pos += 4;
			len -= 4; 
		}
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
			record.numOfThisDisk = DataUtil.Read16U.LE.read(io);
			record.diskWithStartOfCentralDirectory = DataUtil.Read16U.LE.read(io);
			record.nbCentralDirectoryEntriesInThisDisk = DataUtil.Read16U.LE.read(io);
			record.nbCentralDirectoryEntries = DataUtil.Read16U.LE.read(io);
			record.centralDirectorySize = DataUtil.Read32U.LE.read(io);
			record.centralDirectoryOffset = DataUtil.Read32U.LE.read(io);
			int comment_len = DataUtil.Read16U.LE.read(io);
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
			long size = DataUtil.Read64.LE.read(io);
			/* version made by */ DataUtil.Read16U.LE.read(io);
			/* version needed */ DataUtil.Read16U.LE.read(io);
			record.numOfThisDisk = DataUtil.Read32U.LE.read(io);
			record.diskWithStartOfCentralDirectory = DataUtil.Read32U.LE.read(io);
			record.nbCentralDirectoryEntriesInThisDisk = DataUtil.Read64.LE.read(io);
			record.nbCentralDirectoryEntries = DataUtil.Read64.LE.read(io);
			record.centralDirectorySize = DataUtil.Read64.LE.read(io);
			record.centralDirectoryOffset = DataUtil.Read64.LE.read(io);
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
			record.numDiskWithStartOfZip64EndCentralDirectory = DataUtil.Read32U.LE.read(io);
			record.relativeOffsetOfZip64EndOfCentralDirectory = DataUtil.Read64.LE.read(io);
			record.totalNbDisks = DataUtil.Read32U.LE.read(io);
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
