package net.lecousin.dataformat.microsoft.ole;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.microsoft.MicrosoftAbstractDataFormat;
import net.lecousin.dataformat.microsoft.ole.OLEProperty.CodePages;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.buffering.PreBufferedReadable;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;

public class OLEPropertySetStream extends MicrosoftAbstractDataFormat {

	public static final OLEPropertySetStream instance = new OLEPropertySetStream();
	private OLEPropertySetStream() {}
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("MS OLE Property Set Stream");
	}
	
	@Override
	public String[] getFileExtensions() {
		return new String[] {};
	}
	
	@Override
	public String[] getMIMETypes() {
		return new String[] {};
	}
	
	@Override
	public AsyncWork<OLEPropertySets,Exception> getInfo(Data data, byte priority) {
		return readOLEPropertySets(data, priority);
	}
	
	@SuppressWarnings("resource")
	public static AsyncWork<OLEPropertySets,Exception> readOLEPropertySets(Data data, byte priority) {
		AsyncWork<OLEPropertySets,Exception> sp = new AsyncWork<>();
		AsyncWork<? extends IO.Readable.Seekable, Exception> open = data.open(priority);
		open.listenInline(new Runnable() {
			@Override
			public void run() {
				if (open.isCancelled()) return;
				if (!open.isSuccessful()) {
					sp.unblockError(open.getError());
					return;
				}
				@SuppressWarnings("cast")
				IO.Readable.Buffered io = new PreBufferedReadable((IO.Readable)open.getResult(), 512, priority, 4096, priority, 8);
				Reader r = new Reader(io, priority);
				r.startOn(io.canStartReading(), false);
				r.getOutput().listenInline(new Runnable() {
					@Override
					public void run() {
						io.closeAsync();
						if (r.isCancelled()) return;
						if (r.isSuccessful())
							sp.unblockSuccess(r.getResult());
						else
							sp.unblockError(r.getError());
					}
				});
				sp.onCancel(new Listener<CancelException>() {
					@Override
					public void fire(CancelException event) {
						r.cancel(event);
					}
				});
			}
		});
		sp.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				open.unblockCancel(event);
			}
		});
		return sp;
	}
	
	public static class Reader extends Task.Cpu<OLEPropertySets,IOException> {
		public Reader(IO.Readable.Buffered io, byte priority) {
			super("Read OLE Property Set", priority);
			this.io = io;
		}
		private IO.Readable.Buffered io;
		@Override
		public OLEPropertySets run() throws IOException {
			OLEPropertySets sets = new OLEPropertySets();
			io.skip(4);
			sets.systemIdentifier = DataUtil.readUnsignedIntegerLittleEndian(io);
			sets.CLSID = new byte[16];
			io.readFully(sets.CLSID);
			long nbSets = DataUtil.readUnsignedIntegerLittleEndian(io);
			// normally, maximum is 2, we will support up to 256
			if (nbSets > 256) nbSets = 256;
			long[] offsets = new long[(int)nbSets];
			long pos = 0x1C;
			for (int i = 0; i < nbSets; ++i) {
				OLEPropertySet set = new OLEPropertySet();
				sets.propertySets.add(set);
				set.FMTID = new byte[16];
				io.readFully(set.FMTID);
				offsets[i] = DataUtil.readUnsignedIntegerLittleEndian(io);
				pos += 0x14;
			}
			do {
				int next = -1;
				for (int i = 0; i < offsets.length; ++i)
					if (offsets[i] >= pos && (next == -1 || offsets[next] > offsets[i]))
						next = i;
				if (next == -1) break;
				if (offsets[next] != pos)
					io.skipSync(offsets[next]-pos);
				pos = offsets[next];
				OLEPropertySet set = sets.propertySets.get(next);
				
				/*long size =*/ DataUtil.readUnsignedIntegerLittleEndian(io);
				long nb = DataUtil.readUnsignedIntegerLittleEndian(io);
				pos += 8;
				// limit the number of properties
				if (nb > 65536) nb = 65536;
				long[] propertiesIds = new long[(int)nb];
				long[] propertiesOffsets = new long[(int)nb];
				for (int i = 0; i < nb; ++i) {
					propertiesIds[i] = DataUtil.readUnsignedIntegerLittleEndian(io);
					propertiesOffsets[i] = DataUtil.readUnsignedIntegerLittleEndian(io);
					pos += 8;
				}
				for (int i = 0; i < nb; ++i) {
					if (pos != offsets[next] + propertiesOffsets[i])
						io.skipSync(offsets[next] + propertiesOffsets[i] - pos);
					pos = offsets[next] + propertiesOffsets[i];
					if (propertiesIds[i] == 0) {
						// Dictionary property
						set.propertiesNames = new HashMap<>();
						long numEntries = DataUtil.readUnsignedIntegerLittleEndian(io);
						pos += 4;
						// limit to 65536
						if (numEntries > 65536) numEntries = 65536;
						for (int entryIndex = 0; entryIndex < numEntries; ++entryIndex) {
							long id = DataUtil.readUnsignedIntegerLittleEndian(io);
							long len = DataUtil.readUnsignedIntegerLittleEndian(io);
							pos += 8;
							OLEProperty codepage = set.properties.get(new Long(OLEProperty.IDS.CODEPAGE));
							if (codepage instanceof OLEProperty.Short && ((OLEProperty.Short)codepage).value == OLEProperty.CodePages.CP_UTF16) {
								String name = readString(len*2, OLEProperty.CodePages.CP_UTF16);
								int padding = (int)((len*2) % 4);
								if (padding != 0) {
									io.skip(padding); pos += padding;
								}
								set.propertiesNames.put(new Long(id), name);
							} else {
								String name = readString(set, len);
								set.propertiesNames.put(new Long(id), name);
							}
						}
					} else {
						// Typed property entry
						int type = DataUtil.readUnsignedShortLittleEndian(io);
						io.skip(2);
						pos += 4;
						OLEProperty prop;
						switch (type) {
						case 0x0000: // EMPTY
							prop = new OLEProperty.Empty();
							break;
						case 0x0001: // NULL
							prop = new OLEProperty.Null();
							break;
						case 0x0002: // short
							prop = new OLEProperty.Short(DataUtil.readShortLittleEndian(io));
							io.skip(2);
							pos += 4;
							break;
						case 0x0003: case 0x0016: // int
							prop = new OLEProperty.Int(DataUtil.readIntegerLittleEndian(io));
							pos += 4;
							break;
						case 0x0004: // float
							prop = new OLEProperty.Float(Float.intBitsToFloat(DataUtil.readIntegerLittleEndian(io)));
							pos += 4;
							break;
						case 0x0005: // double
							prop = new OLEProperty.Double(Double.longBitsToDouble(DataUtil.readLongLittleEndian(io)));
							pos += 8;
							break;
						case 0x0006: // currency
							prop = new OLEProperty.Currency(((double)DataUtil.readLongLittleEndian(io))/10000);
							pos += 8;
							break;
						case 0x0007: { // date
							double v = Double.longBitsToDouble(DataUtil.readLongLittleEndian(io));
							Calendar c = Calendar.getInstance();
							c.set(Calendar.YEAR, 0);
							c.set(Calendar.MONTH, 0);
							c.set(Calendar.DAY_OF_MONTH, 1);
							c.set(Calendar.HOUR, 0);
							c.set(Calendar.MINUTE, 0);
							c.set(Calendar.SECOND, 0);
							c.set(Calendar.MILLISECOND, 0);
							long l = (long)Math.floor(v);
							while (l > 0) {
								int d = l > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)l;
								c.add(Calendar.DAY_OF_MONTH, d);
								l -= d;
							}
							v -= l;
							v *= 24*60*60*1000;
							l = (long)Math.floor(v);
							while (l > 0) {
								int m = l > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)l;
								c.add(Calendar.MILLISECOND, m);
								l -= m;
							}
							prop = new OLEProperty.Date(c.getTimeInMillis());
							pos += 8;
							break; }
						case 0x0008: case 0x001E: { // code page string
							long len = DataUtil.readUnsignedIntegerLittleEndian(io);
							pos += 4;
							String s = readString(set, len);
							pos += len;
							int padding = (int)(len % 4);
							if (padding != 0) {
								io.skip(padding);
								pos += padding;
							}
							prop = new OLEProperty.CodePageString(s);
							break; }
						case 0x000A: // Error
							prop = new OLEProperty.Error(DataUtil.readUnsignedIntegerLittleEndian(io));
							pos += 4;
							break;
						case 0x000B: // boolean
							prop = new OLEProperty.Bool(DataUtil.readUnsignedShortLittleEndian(io) == 0xFFFF);
							io.skip(2);
							pos += 4;
							break;
						case 0x000E: // decimal
							prop = null; // TODO
							break;
						case 0x0010: // byte
							prop = new OLEProperty.Byte((byte)io.read());
							io.skip(3);
							pos += 4;
							break;
						case 0x0011: // unsigned byte
							prop = new OLEProperty.UnsignedByte((short)io.read());
							io.skip(3);
							pos += 4;
							break;
						case 0x0012: // unsigned short
							prop = new OLEProperty.UnsignedShort(DataUtil.readUnsignedShortLittleEndian(io));
							io.skip(2);
							pos += 4;
							break;
						case 0x0013: case 0x0017:
							prop = new OLEProperty.UnsignedInt(DataUtil.readUnsignedIntegerLittleEndian(io));
							pos += 4;
							break;
						case 0x0014:
							prop = new OLEProperty.Long(DataUtil.readLongLittleEndian(io));
							pos += 8;
							break;
						case 0x0015:
							prop = new OLEProperty.UnsignedLong(DataUtil.readLongLittleEndian(io));
							pos += 8;
							break;
						case 0x001F: { // unicode
							long len = DataUtil.readUnsignedIntegerLittleEndian(io);
							String s = readString(len*2, CodePages.CP_UNICODE);
							pos += len;
							int padding = (int)((len*2) % 4);
							if (padding > 0) {
								io.skip(padding);
								pos += padding;
							}
							prop = new OLEProperty.Str(s);
							break; }
						case 0x0040: { // filetime
							long low = DataUtil.readUnsignedIntegerLittleEndian(io);
							long high = DataUtil.readUnsignedIntegerLittleEndian(io);
							long filetime = high << 32 | (low & 0xffffffffL);
							long ms_since_16010101 = filetime / (1000 * 10);
					        long ms_since_19700101 = ms_since_16010101 - OLEProperty.EPOCH_DIFF;
							prop = new OLEProperty.Date(ms_since_19700101);
							pos += 8;
							break; }
						case 0x0041: // blob
							prop = new OLEProperty.Bytes(pos+4, DataUtil.readUnsignedIntegerLittleEndian(io));
							break;
						case 0x0042: { // stream
							prop = null; // TODO
							break; }
						case 0x0043: { // storage
							prop = null; // TODO
							break; }
						case 0x0044: { // streamed object
							prop = null; // TODO
							break; }
						case 0x0045: { // stored object
							prop = null; // TODO
							break; }
						case 0x0046: { // blob object
							prop = null; // TODO
							break; }
						case 0x0047: { // property identifier
							prop = null; // TODO
							break; }
						case 0x0048: { // CLSID
							prop = null; // TODO
							break; }
						case 0x0049: { // Versioned stream
							prop = null; // TODO
							break; }
						case 0x1002: { // vector of short
							prop = null; // TODO
							break; }
						case 0x1003: { // vector of int
							prop = null; // TODO
							break; }
						case 0x1004: { // vector of float
							prop = null; // TODO
							break; }
						case 0x1005: { // vector of double
							prop = null; // TODO
							break; }
						case 0x1006: { // vector of currency
							prop = null; // TODO
							break; }
						case 0x1007: { // vector of date
							prop = null; // TODO
							break; }
						case 0x1008: { // vector of code page string
							prop = null; // TODO
							break; }
						case 0x100A: { // vector of Error
							prop = null; // TODO
							break; }
						case 0x100B: { // vector of bool
							prop = null; // TODO
							break; }
						case 0x100C: { // Vector of variable-typed properties
							prop = null; // TODO
							break; }
						// TODO continue
						default:
							LCCore.getApplication().getDefaultLogger().error("Unknown OLE Property type: 0x"+Integer.toHexString(type));
							prop = null;
							break;
						}
						if (prop != null)
							set.properties.put(new Long(propertiesIds[i]), prop);
					}
				}
			} while (true);
			return sets;
		}
		
		private String readString(OLEPropertySet set, long len) throws IOException {
			OLEProperty codepage = set.properties.get(new Long(OLEProperty.IDS.CODEPAGE));
			if (codepage instanceof OLEProperty.Short)
				return readString(len, ((OLEProperty.Short)codepage).value);
			return readString(len, 0);
		}

		private String readString(long len, int codepage) throws IOException {
			int l = len > 65536 ? 65536 : (int)len;
			byte[] bytes = new byte[l];
			io.readFully(bytes);
			if (len > l) io.skipSync(len - l);
			while (l > 0 && bytes[l-1] == 0) l--;
			String charset = OLEProperty.CodePages.codepageToEncoding(codepage);
			if (charset == null) return "";
			return new String(bytes, 0, l, charset);
		}
	}
	
}
