package net.lecousin.dataformat.executable.windows.coff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO.Readable.Seekable;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.math.RangeLong;

public class COFFDetector implements DataFormatDetector.MoreThanHeaderNeeded {

	@Override
	public DataFormat[] getDetectedFormats() {
		return new DataFormat[] { COFFDataFormat.instance };
	}
	
	@Override
	public Signature[] getHeaderSignature() {
		return null;
	}
	
	@Override
	public AsyncWork<DataFormat,NoException> finishDetection(Data data, byte[] header, int headerLength, Seekable io, long dataSize) {
		if (headerLength < 20)
			return new AsyncWork<DataFormat,NoException>(null, null);
		int nb_sections = DataUtil.readShortLittleEndian(header, 2);
		if (nb_sections <= 0) return new AsyncWork<DataFormat,NoException>(null, null);
		long symbolPointer = DataUtil.readUnsignedIntegerLittleEndian(header, 8);
		long nbSymbols = DataUtil.readUnsignedIntegerLittleEndian(header, 12);
		if (symbolPointer == 0) {
			if (nbSymbols != 0)
				return new AsyncWork<DataFormat,NoException>(null, null);
		} else {
			if (symbolPointer > dataSize)
				return new AsyncWork<DataFormat,NoException>(null, null);
		}
		int size_opt_headers = DataUtil.readUnsignedShortLittleEndian(header, 16);
		long pos = 20+size_opt_headers;
		if (dataSize < pos+nb_sections*40)
			return new AsyncWork<DataFormat,NoException>(null, null);
		List<RangeLong> sections = new LinkedList<RangeLong>();
		for (int i = 0; i < nb_sections; ++i) {
			if (pos+40 > headerLength) {
				byte[] buf = new byte[40];
				ByteBuffer buffer = ByteBuffer.wrap(buf);
				AsyncWork<DataFormat,NoException> result = new AsyncWork<DataFormat,NoException>();
				asyncDetect(data, io, pos, i, nb_sections, dataSize, sections, buf, buffer, result);
				return result;
			}
			long size = DataUtil.readUnsignedIntegerLittleEndian(header, (int)pos+16);
			long start = DataUtil.readUnsignedIntegerLittleEndian(header, (int)pos+20);
			if (start + size > dataSize)
				return new AsyncWork<DataFormat,NoException>(null, null);
			if (size > 0) {
				RangeLong range = new RangeLong(start, start+size-1);
				for (RangeLong r : sections)
					if (range.intersect(r) != null)
						return new AsyncWork<DataFormat,NoException>(null, null);
				sections.add(range);
			}
			pos += 40;
		}
		data.setProperty("COFFOffset", new Long(0));
		return new AsyncWork<DataFormat,NoException>(COFFDataFormat.instance, null);
	}
	
	private void asyncDetect(Data data, Seekable io, long pos, int sectionIndex, int nb_sections, long dataSize, List<RangeLong> sections, byte[] buf, ByteBuffer buffer, AsyncWork<DataFormat,NoException> result) {
		if (sectionIndex == nb_sections) {
			data.setProperty("COFFOffset", new Long(0));
			result.unblockSuccess(COFFDataFormat.instance);
			return;
		}
		buffer.clear();
		AsyncWork<Integer,IOException> read = io.readFullyAsync(pos, buffer);
		Task<Void,NoException> task = new Task.Cpu<Void,NoException>("Check COFF section", io.getPriority()) {
			@Override
			public Void run() {
				if (!read.isSuccessful()) {
					result.unblockSuccess(null);
					return null;
				}
				if (read.getResult().intValue() != 40) {
					result.unblockSuccess(null);
					return null;
				}
				long size = DataUtil.readUnsignedIntegerLittleEndian(buf, 16);
				long start = DataUtil.readUnsignedIntegerLittleEndian(buf, 20);
				if (start + size > dataSize) {
					result.unblockSuccess(null);
					return null;
				}
				if (size > 0) {
					RangeLong range = new RangeLong(start, start+size-1);
					for (RangeLong r : sections)
						if (range.intersect(r) != null){
							result.unblockSuccess(null);
							return null;
						}
					sections.add(range);
				}
				asyncDetect(data, io, pos+40, sectionIndex+1, nb_sections, dataSize, sections, buf, buffer, result);
				return null;
			}
		};
		read.listenAsync(task, true);
	}
	
}
