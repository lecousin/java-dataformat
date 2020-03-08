package net.lecousin.dataformat.executable.windows.versioninfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.executable.windows.versioninfo.VersionInfo.DriverType;
import net.lecousin.dataformat.executable.windows.versioninfo.VersionInfo.FontType;
import net.lecousin.dataformat.executable.windows.versioninfo.VersionInfo.Type;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class VersionInfoDataFormat implements DataFormat {

	public static final VersionInfoDataFormat instance = new VersionInfoDataFormat();
	
	@Override
	public ILocalizableString getName() { return new FixedLocalizedString("Version Info"); }

	@Override
	public AsyncSupplier<VersionInfo, Exception> getInfo(Data data, Priority priority) {
		AsyncSupplier<VersionInfo, Exception> result = new AsyncSupplier<VersionInfo, Exception>();
		AsyncSupplier<? extends IO.Readable.Buffered,IOException> open = data.openReadOnly(priority);
		open.onDone(new Runnable() {
			@Override
			public void run() {
				if (!open.isSuccessful()) {
					if (open.isCancelled()) result.unblockCancel(open.getCancelEvent());
					else result.unblockError(open.getError());
					return;
				}
				IO.Readable.Buffered io = open.getResult();
				Task<Void, NoException> taskRead = Task.cpu("Read VERSION_INFO", priority, t -> {
					try {
						int length = DataUtil.Read16U.LE.read(io);
						byte[] buf = new byte[length];
						AsyncSupplier<Integer,IOException> read = io.readFullyAsync(ByteBuffer.wrap(buf,2,length-2));
						ReadVersionInfo taskReadVersionInfo = new ReadVersionInfo(buf, result);
						read.thenStart(Task.cpu("Parsing VERSION_INFO", priority, taskReadVersionInfo), true);
						read.onDone(new Runnable() {
							@Override
							public void run() {
								io.closeAsync();
							}
						});
						return null;
					} catch (Exception e) {
						result.unblockError(e);
						io.closeAsync();
						return null;
					}
				});
				io.canStartReading().thenStart(taskRead, true);
			}
		});
		return result;
	}
	
	private static class ReadVersionInfo implements Executable<VersionInfo,NoException> {
		private ReadVersionInfo(byte[] buf, AsyncSupplier<VersionInfo, Exception> result) {
			this.buf = buf;
			this.result = result;
		}
		private byte[] buf;
		private AsyncSupplier<VersionInfo, Exception> result;
		@Override
		public VersionInfo execute(Task<VersionInfo,NoException> taskContext) {
			VersionInfo info = new VersionInfo();
			
			int value_len = DataUtil.Read16U.LE.read(buf, 2);
			//int type = IOUtil.readUnsignedShortIntel(tmp, 4); //1 for text, 0 for binary
			// here is VS_VERSION_INFO unicode string
			int pos = 6 + 32;
			pos += 2; // padding to align on 32-bit
			if (value_len == 0x34)
				read_fixed_file_info(info, buf, pos);
			pos += value_len;
			while ((pos % 4) != 0) pos++; // padding
			while (pos < buf.length) {
				int len = DataUtil.Read16U.LE.read(buf, pos);
				if (len == 0) break;
				if (pos + len > buf.length) break;
				//value_len = IOUtil.readUnsignedShortIntel(tmp, pos+2); // always 0...
				//type = IOUtil.readUnsignedShortIntel(tmp, pos+4);
				StringBuilder s = new StringBuilder();
				int i = pos +6;
				while (i < pos+len) {
					char c = (char)(((buf[i++] & 0xFF)) | ((buf[i++] & 0xFF) << 8));
					if (c == 0) break;
					s.append(c);
				}
				while ((i % 4) != 0) i++; // padding;
				if (s.toString().equals("StringFileInfo")) {
					while (i < pos+len) {
						while ((i % 4) != 0) i++; // padding;
						i += read_string_table(info, buf, i);
					}
				} else if (s.toString().equals("VarFileInfo")) {
					// TODO may it be really useful ?
				}
				pos += len;
			}
			
			for (int lang : info.language_strings.keySet()) { // TODO make localized strings
				Map<String,String> strings = info.language_strings.get(new Integer(lang));
				if (info.productName == null) { info.productName = strings.get("ProductName"); if (info.productName != null) info.productName = info.productName.trim(); }
				if (info.productVersion == null) { info.productVersion = strings.get("ProductVersion"); if (info.productVersion != null) info.productVersion = info.productVersion.trim(); }
				if (info.publisher == null) { info.publisher = strings.get("CompanyName"); if (info.publisher != null) info.publisher = info.publisher.trim(); }
				if (info.description == null) { info.description = strings.get("FileDescription"); if (info.description != null) info.description = info.description.trim(); }
			}
			
			result.unblockSuccess(info);
			return info;
		}
	}
	
	private static int read_string_table(VersionInfo md, byte[] tmp, int pos) {
		int len = DataUtil.Read16U.LE.read(tmp, pos);
		if (pos + len > tmp.length) return tmp.length;
		char[] lang = new char[4];
		char[] code_page = new char[4];
		for (int i = 0; i < 4; ++i)
			lang[i] = (char)(((tmp[pos+6+i*2] & 0xFF)) | ((tmp[pos+6+i*2+1] & 0xFF) << 8));
		for (int i = 0; i < 4; ++i)
			code_page[i] = (char)(((tmp[pos+14+i*2] & 0xFF)) | ((tmp[pos+14+i*2+1] & 0xFF) << 8));
		Map<String,String> strings = new HashMap<String,String>();
		int i = 0;
		while (22+i < len) {
			while (((pos+22+i) % 4) != 0) i++;
			i += read_string(tmp, pos+22+i, strings);
		}
		int language = 0;
		try { language = Integer.parseInt(new String(lang)); }
		catch (NumberFormatException e) {}
		md.language_strings.put(new Integer(language), strings);
		return len;
	}
	
	private static int read_string(byte[] tmp, int pos, Map<String,String> strings) {
		int len = DataUtil.Read16U.LE.read(tmp, pos);
		if (len == 0) return tmp.length;
		if (pos + len > tmp.length) return pos+len;
		StringBuilder name = new StringBuilder();
		int i = pos +6;
		while (i < pos+len) {
			char c = (char)(((tmp[i++] & 0xFF)) | ((tmp[i++] & 0xFF) << 8));
			if (c == 0) break;
			name.append(c);
		}
		while ((i % 4) != 0) i++; // padding;
		StringBuilder value = new StringBuilder();
		while (i < pos+len) {
			char c = (char)(((tmp[i++] & 0xFF)) | ((tmp[i++] & 0xFF) << 8));
			if (c == 0) break;
			value.append(c);
		}
		strings.put(name.toString(), value.toString());
		return len;
	}
	
	private static void read_fixed_file_info(VersionInfo md, byte[] tmp, int pos) {
		if (tmp[pos] != (byte)0xBD || tmp[pos+1] != (byte)0x04 || tmp[pos+2] != (byte)0xEF || tmp[pos+3] != (byte)0xFE) return;
		long type = DataUtil.Read32U.LE.read(tmp, pos+36);
		long sub_type = DataUtil.Read32U.LE.read(tmp, pos+40);
		switch ((int)type) {
		case 1: md.type = Type.Application; break;
		case 2: md.type = Type.Dynamic_Library; break;
		case 3: 
			md.type = Type.Driver;
			switch ((int)sub_type) {
			case 0x01: md.sub_type = DriverType.Printer; break;
			case 0x02: md.sub_type = DriverType.Keyboard; break;
			case 0x03: md.sub_type = DriverType.Language; break;
			case 0x04: md.sub_type = DriverType.Display; break;
			case 0x05: md.sub_type = DriverType.Mouse; break;
			case 0x06: md.sub_type = DriverType.Network; break;
			case 0x07: md.sub_type = DriverType.System; break;
			case 0x08: md.sub_type = DriverType.Installable; break;
			case 0x09: md.sub_type = DriverType.Sound; break;
			case 0x0A: md.sub_type = DriverType.Communications; break;
			case 0x0C: md.sub_type = DriverType.VersionedPrinter; break;
			}
			break;
		case 4: 
			md.type = Type.Font;
			switch ((int)sub_type) {
			case 0x01: md.sub_type = FontType.Raster; break;
			case 0x02: md.sub_type = FontType.Vector; break;
			case 0x03: md.sub_type = FontType.TrueType; break;
			}
			break;
		case 5: md.type = Type.VirtualDevice; break;
		case 7: md.type = Type.Static_Library; break;
		}
	}

	@Override
	public IconProvider getIconProvider() { return null; } // TODO

	@Override
	public String[] getFileExtensions() {
		return null;
	}

	@Override
	public String[] getMIMETypes() {
		return null;
	}
	
}
