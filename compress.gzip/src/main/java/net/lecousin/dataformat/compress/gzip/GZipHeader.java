package net.lecousin.dataformat.compress.gzip;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.SingleBufferReadable;
import net.lecousin.framework.io.util.DataUtil;

public class GZipHeader {

	public byte compressionMethod;
	public byte flags;
	/** in seconds, EPOCH timestamp. */
	public long modificationTime;
	public String filename;
	public String comment;
	
	/** file is probably ASCII text. */
	public static byte FLAG_FTEXT = 0x01;
	/** a CRC16 for the gzip header is present. */
	public static byte FLAG_HCRC = 0x02;
	/** optional extra fields are present. */
	public static byte FLAG_EXTRA = 0x04;
	/** an original file name is present. */
	public static byte FLAG_NAME = 0x08;
	/** a zero-terminated file comment is present. */
	public static byte FLAG_COMMENT = 0x10;
	
	public SynchronizationPoint<IOException> read(IO.Readable io) {
		SynchronizationPoint<IOException> sp = new SynchronizationPoint<>();
		byte[] buf = new byte[6];
		io.skipAsync(2).listenInline(() -> {
			io.readFullyAsync(ByteBuffer.wrap(buf)).listenInline(() -> {
				compressionMethod = buf[0];
				flags = buf[1];
				modificationTime = DataUtil.readUnsignedIntegerLittleEndian(buf, 2);
				if ((flags & (FLAG_NAME | FLAG_COMMENT)) == 0) {
					sp.unblock();
					return;
				}
				// skip XFL + OS
				io.skipAsync(2).listenInline(() -> {
					if ((flags & FLAG_EXTRA) != 0) {
						// read extra len
						io.readFullyAsync(ByteBuffer.wrap(buf, 0, 2)).listenInline(() -> {
							int extraLen = DataUtil.readUnsignedShortLittleEndian(buf, 0);
							// skip extra
							io.skipAsync(extraLen).listenInline(() -> {
								readStrings(io, sp);
							}, sp);
						}, sp);
					} else
						readStrings(io, sp);
				}, sp);
			}, sp);
		}, sp);
		return sp;
	}
	
	@SuppressWarnings("resource")
	private void readStrings(IO.Readable io, SynchronizationPoint<IOException> sp) {
		IO.Readable.Buffered bio;
		if (io instanceof IO.Readable.Buffered)
			bio = (IO.Readable.Buffered)io;
		else
			bio = new SingleBufferReadable(io, 256, false);
		bio.canStartReading().listenInline(() -> {
			try {
				if ((flags & FLAG_NAME) != 0)
					filename = readString(bio);
				if ((flags & FLAG_COMMENT) != 0)
					comment = readString(bio);
				sp.unblock();
			} catch (IOException e) {
				sp.error(e);
			}
		}, sp);
	}
	
	private static String readString(IO.Readable.Buffered io) throws IOException {
		StringBuilder s = new StringBuilder();
		byte b;
		while ((b = io.readByte()) != 0)
			s.append((char)b);
		return s.toString();
	}
	
}
