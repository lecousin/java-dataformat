package net.lecousin.dataformat.executable.unix;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.io.util.DataUtil;

public class ELFDetector implements DataFormatDetector.OnlyHeaderNeeded {

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] {
			ELFDataFormat.instance
		};
	}
	
	@Override
	public Signature[] getHeaderSignature() {
		return new Signature[] {
			new Signature((short)0, new byte[] { 0x7F, 'E', 'L', 'F' })
		};
	}
	
	@Override
	public DataFormat finishDetection(Data data, byte[] header, int headerLength, long dataSize) {
		if (headerLength < 0x34)
			return null;
		ELFInfo info = new ELFInfo();
		switch (header[0x04]) {
		case 1: info.bits = ELFInfo.Bits._32; break;
		case 2: info.bits = ELFInfo.Bits._64; break;
		default: return null;
		}
		switch (header[0x05]) {
		case 1: info.endianness = ELFInfo.Endianness.LE; break;
		case 2: info.endianness = ELFInfo.Endianness.BE; break;
		default: return null;
		}
		info.targetOS = ELFInfo.TargetOS.get(header[0x07]);
		switch (header[0x10]) {
		case 1: info.type = ELFInfo.Type.RELOCATABLE; break;
		case 2: info.type = ELFInfo.Type.EXECUTABLE; break;
		case 3: info.type = ELFInfo.Type.SHARED; break;
		case 4: info.type = ELFInfo.Type.CORE; break;
		default: return null;
		}
		switch (info.bits) {
		case _32:
			switch (info.endianness) {
			case LE:
				info.programHeaderTableOffset = DataUtil.Read32U.LE.read(header, 0x1C);
				info.sectionHeaderTableOffset = DataUtil.Read32U.LE.read(header, 0x20);
				info.programHeaderTableEntrySize = DataUtil.Read16U.LE.read(header, 0x2A);
				info.programHeaderTableNbEntries = DataUtil.Read16U.LE.read(header, 0x2C);
				info.sectionHeaderTableEntrySize = DataUtil.Read16U.LE.read(header, 0x2E);
				info.sectionHeaderTableNbEntries = DataUtil.Read16U.LE.read(header, 0x30);
				info.sectionNamesIndex = DataUtil.Read16U.LE.read(header, 0x32);
				break;
			case BE:
				info.programHeaderTableOffset = DataUtil.Read32U.BE.read(header, 0x1C);
				info.sectionHeaderTableOffset = DataUtil.Read32U.BE.read(header, 0x20);
				info.programHeaderTableEntrySize = DataUtil.Read16U.BE.read(header, 0x2A);
				info.programHeaderTableNbEntries = DataUtil.Read16U.BE.read(header, 0x2C);
				info.sectionHeaderTableEntrySize = DataUtil.Read16U.BE.read(header, 0x2E);
				info.sectionHeaderTableNbEntries = DataUtil.Read16U.BE.read(header, 0x30);
				info.sectionNamesIndex = DataUtil.Read16U.BE.read(header, 0x32);
				break;
			}
			break;
		case _64:
			if (headerLength < 0x40) return null;
			switch (info.endianness) {
			case LE:
				info.programHeaderTableOffset = DataUtil.Read64.LE.read(header, 0x20);
				info.sectionHeaderTableOffset = DataUtil.Read64.LE.read(header, 0x28);
				info.programHeaderTableEntrySize = DataUtil.Read16U.LE.read(header, 0x36);
				info.programHeaderTableNbEntries = DataUtil.Read16U.LE.read(header, 0x38);
				info.sectionHeaderTableEntrySize = DataUtil.Read16U.LE.read(header, 0x3A);
				info.sectionHeaderTableNbEntries = DataUtil.Read16U.LE.read(header, 0x3C);
				info.sectionNamesIndex = DataUtil.Read16U.LE.read(header, 0x3E);
				break;
			case BE:
				info.programHeaderTableOffset = DataUtil.Read64.BE.read(header, 0x20);
				info.sectionHeaderTableOffset = DataUtil.Read64.BE.read(header, 0x28);
				info.programHeaderTableEntrySize = DataUtil.Read16U.BE.read(header, 0x36);
				info.programHeaderTableNbEntries = DataUtil.Read16U.BE.read(header, 0x38);
				info.sectionHeaderTableEntrySize = DataUtil.Read16U.BE.read(header, 0x3A);
				info.sectionHeaderTableNbEntries = DataUtil.Read16U.BE.read(header, 0x3C);
				info.sectionNamesIndex = DataUtil.Read16U.BE.read(header, 0x3E);
				break;
			}
			break;
		}
		// save info in data
		data.setProperty(ELFInfo.PROPERTY_NAME, info);
		return ELFDataFormat.instance;
	}
	
}
