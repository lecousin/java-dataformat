package net.lecousin.dataformat.executable.unix;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.provider.IOProvider.Readable;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

public class ELFDataFormat implements DataFormat.DataContainerFlat {
	
	public static final ELFDataFormat instance = new ELFDataFormat();
	
	private ELFDataFormat() {}
	
	@Override
	public ILocalizableString getName() {
		// TODO Auto-generated method stub
		return new FixedLocalizedString("Unix exectuable");
	}
	
	public static final IconProvider iconProvider = new IconProvider.FromPath("net/lecousin/dataformat/executable/unix/linux_", ".png", 16, 32, 48, 64, 128);
	@Override
	public IconProvider getIconProvider() { return iconProvider; }
	
	public static final String[] extensions = { "so", "axf", "bin", "elf", "o", "prx", "puff" };
	public static final String[] mimes = {  };
	
	@Override
	public String[] getFileExtensions() {
		return extensions;
	}
	
	@Override
	public String[] getMIMETypes() {
		return mimes;
	}
	
	@Override
	public AsyncWork<? extends DataFormatInfo, ?> getInfo(Data data, byte priority) {
		ELFInfo info = (ELFInfo)data.getProperty(ELFInfo.PROPERTY_NAME);
		return new AsyncWork<>(info, null);
	}
	
	@Override
	public void populateSubData(Data data, AsyncCollection<Data> list) {
		ELFInfo info = (ELFInfo)data.getProperty(ELFInfo.PROPERTY_NAME);
		AsyncWork<? extends IO.Readable.Seekable,Exception> open = data.open(Task.PRIORITY_NORMAL);
		byte[] sectionEntryBuffer = new byte[info.sectionHeaderTableEntrySize];
		open.listenInline(new Runnable() {
			@Override
			public void run() {
				if (!open.isSuccessful()) {
					list.done();
					return;
				}
				readNamesSection(data, open.getResult(), info, sectionEntryBuffer, list);
			}
		});
	}

	private static void readNamesSection(Data data, IO.Readable.Seekable io, ELFInfo info, byte[] sectionEntryBuffer, AsyncCollection<Data> list) {
		AsyncWork<Integer,IOException> read = io.readAsync(info.sectionHeaderTableOffset+info.sectionNamesIndex*info.sectionHeaderTableEntrySize, ByteBuffer.wrap(sectionEntryBuffer));
		read.listenAsync(new Task.Cpu<Void,NoException>("Read ELF names section", Task.PRIORITY_NORMAL) {
			@Override
			public Void run() {
				if (!read.isSuccessful()) {
					list.done();
					return null;
				}
				long offset = 0, size = 0;
				switch (info.bits) {
				case _32:
					switch (info.endianness) {
					case LE:
						offset = DataUtil.readUnsignedIntegerLittleEndian(sectionEntryBuffer, 0x10);
						size = DataUtil.readUnsignedIntegerLittleEndian(sectionEntryBuffer, 0x14);
						break;
					case BE:
						offset = DataUtil.readUnsignedIntegerBigEndian(sectionEntryBuffer, 0x10);
						size = DataUtil.readUnsignedIntegerBigEndian(sectionEntryBuffer, 0x14);
						break;
					}
					break;
				case _64:
					switch (info.endianness) {
					case LE:
						offset = DataUtil.readLongLittleEndian(sectionEntryBuffer, 0x20);
						size = DataUtil.readLongLittleEndian(sectionEntryBuffer, 0x28);
						break;
					case BE:
						offset = DataUtil.readLongBigEndian(sectionEntryBuffer, 0x20);
						size = DataUtil.readLongBigEndian(sectionEntryBuffer, 0x28);
						break;
					}
					break;
				}
				if (size > 128*1024) {
					// ????
					list.done();
					return null;
				}
				byte[] names = new byte[(int)size];
				AsyncWork<Integer,IOException> readNames = io.readAsync(offset, ByteBuffer.wrap(names));
				readNames.listenInline(new Runnable() {
					@Override
					public void run() {
						if (!readNames.isSuccessful()) {
							list.done();
							return;
						}
						readSection(data, io, info, sectionEntryBuffer, 0, names, list);
					}
				});
				return null;
			}
		}, true);
	}
	
	private static void readSection(Data data, IO.Readable.Seekable io, ELFInfo info, byte[] sectionEntryBuffer, int sectionIndex, byte[] names, AsyncCollection<Data> list) {
		if (sectionIndex == info.sectionNamesIndex) {
			readSection(data, io, info, sectionEntryBuffer, sectionIndex+1, names, list);
			return;
		}
		if (sectionIndex == info.sectionHeaderTableNbEntries) {
			list.done();
			return;
		}
		AsyncWork<Integer,IOException> read = io.readAsync(info.sectionHeaderTableOffset+sectionIndex*info.sectionHeaderTableEntrySize, ByteBuffer.wrap(sectionEntryBuffer));
		read.listenAsync(new Task.Cpu<Void,NoException>("Read ELF section", Task.PRIORITY_NORMAL) {
			@Override
			public Void run() {
				if (!read.isSuccessful()) {
					list.done();
					return null;
				}
				long nameOffset = 0, offset = 0, size = 0;
				switch (info.bits) {
				case _32:
					switch (info.endianness) {
					case LE:
						nameOffset = DataUtil.readUnsignedIntegerLittleEndian(sectionEntryBuffer, 0x00);
						offset = DataUtil.readUnsignedIntegerLittleEndian(sectionEntryBuffer, 0x10);
						size = DataUtil.readUnsignedIntegerLittleEndian(sectionEntryBuffer, 0x14);
						break;
					case BE:
						nameOffset = DataUtil.readUnsignedIntegerBigEndian(sectionEntryBuffer, 0x00);
						offset = DataUtil.readUnsignedIntegerBigEndian(sectionEntryBuffer, 0x10);
						size = DataUtil.readUnsignedIntegerBigEndian(sectionEntryBuffer, 0x14);
						break;
					}
					break;
				case _64:
					switch (info.endianness) {
					case LE:
						nameOffset = DataUtil.readLongLittleEndian(sectionEntryBuffer, 0x00);
						offset = DataUtil.readLongLittleEndian(sectionEntryBuffer, 0x20);
						size = DataUtil.readLongLittleEndian(sectionEntryBuffer, 0x28);
						break;
					case BE:
						nameOffset = DataUtil.readLongBigEndian(sectionEntryBuffer, 0x00);
						offset = DataUtil.readLongBigEndian(sectionEntryBuffer, 0x20);
						size = DataUtil.readLongBigEndian(sectionEntryBuffer, 0x28);
						break;
					}
					break;
				}
				StringBuilder name = new StringBuilder();
				if (nameOffset < names.length) {
					int i = (int)nameOffset;
					while (i < names.length) {
						if (names[i] == 0) break;
						name.append((char)(names[i]&0xFF));
						i++;
					}
				}
				list.newElements(Collections.singletonList(new SubData(data, offset, size, name.toString())));
				readSection(data, io, info, sectionEntryBuffer, sectionIndex+1, names, list);
				return null;
			}
		}, true);
	}
	
	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return null;
	}
	
	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
	}

	@Override
	public boolean canRenameSubData(Data data, Data subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> renameSubData(Data data, Data subData, String newName, byte priority) {
		return null;
	}

	@Override
	public boolean canRemoveSubData(Data data, List<Data> subData) {
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> removeSubData(Data data, List<Data> subData, byte priority) {
		return null;
	}

	@Override
	public boolean canAddSubData(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<Exception> addSubData(Data data, List<Pair<String, Readable>> subData, byte priority) {
		return null;
	}
	
	@Override
	public AsyncWork<Pair<Data, Writable>, ? extends Exception> createSubData(Data data, String name, byte priority) {
		return null;
	}

	@Override
	public boolean canCreateDirectory(Data parent) {
		return false;
	}

	@Override
	public ISynchronizationPoint<? extends Exception> createDirectory(Data parent, String name, byte priority) {
		return null;
	}
}
