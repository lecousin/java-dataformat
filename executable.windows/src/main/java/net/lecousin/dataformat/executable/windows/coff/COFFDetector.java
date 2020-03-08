package net.lecousin.dataformat.executable.windows.coff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatDetector;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Readable.Seekable;
import net.lecousin.framework.io.IOUtil;
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
	public AsyncSupplier<DataFormat,NoException> finishDetection(Data data, byte[] header, int headerLength, IO.Readable.Seekable io, long dataSize) {
		if (headerLength < 20)
			return new AsyncSupplier<DataFormat,NoException>(null, null);
		int nb_sections = DataUtil.Read16.LE.read(header, 2);
		if (nb_sections <= 0) return new AsyncSupplier<DataFormat,NoException>(null, null);
		if (dataSize < 0)
			try { dataSize = IOUtil.getSizeSync(io); }
			catch (IOException e) { return new AsyncSupplier<DataFormat,NoException>(null, null); }
		long symbolPointer = DataUtil.Read32U.LE.read(header, 8);
		long nbSymbols = DataUtil.Read32U.LE.read(header, 12);
		if (symbolPointer == 0) {
			if (nbSymbols != 0)
				return new AsyncSupplier<DataFormat,NoException>(null, null);
		} else {
			if (symbolPointer > dataSize)
				return new AsyncSupplier<DataFormat,NoException>(null, null);
		}
		int size_opt_headers = DataUtil.Read16U.LE.read(header, 16);
		long pos = 20+size_opt_headers;
		if (dataSize < pos+nb_sections*40)
			return new AsyncSupplier<DataFormat,NoException>(null, null);
		List<RangeLong> sections = new LinkedList<RangeLong>();
		for (int i = 0; i < nb_sections; ++i) {
			if (pos+40 > headerLength) {
				byte[] buf = new byte[40];
				ByteBuffer buffer = ByteBuffer.wrap(buf);
				AsyncSupplier<DataFormat,NoException> result = new AsyncSupplier<DataFormat,NoException>();
				asyncDetect(data, io, pos, i, nb_sections, dataSize, sections, buf, buffer, result);
				return result;
			}
			long size = DataUtil.Read32U.LE.read(header, (int)pos+16);
			long start = DataUtil.Read32U.LE.read(header, (int)pos+20);
			if (start + size > dataSize)
				return new AsyncSupplier<DataFormat,NoException>(null, null);
			if (size > 0) {
				RangeLong range = new RangeLong(start, start+size-1);
				for (RangeLong r : sections)
					if (range.intersect(r) != null)
						return new AsyncSupplier<DataFormat,NoException>(null, null);
				sections.add(range);
			}
			if (size == 0 && start == 0 && DataUtil.Read64.LE.read(header, (int)pos) == 0)
				return new AsyncSupplier<DataFormat,NoException>(null, null);
			pos += 40;
		}
		data.setProperty("COFFOffset", new Long(0));
		return new AsyncSupplier<DataFormat,NoException>(COFFDataFormat.instance, null);
	}
	
	private void asyncDetect(Data data, Seekable io, long pos, int sectionIndex, int nb_sections, long dataSize, List<RangeLong> sections, byte[] buf, ByteBuffer buffer, AsyncSupplier<DataFormat,NoException> result) {
		if (sectionIndex == nb_sections) {
			data.setProperty("COFFOffset", new Long(0));
			result.unblockSuccess(COFFDataFormat.instance);
			return;
		}
		buffer.clear();
		AsyncSupplier<Integer,IOException> read = io.readFullyAsync(pos, buffer);
		Task<Void,NoException> task = Task.cpu("Check COFF section", io.getPriority(), t -> {
			if (!read.isSuccessful()) {
				result.unblockSuccess(null);
				return null;
			}
			if (read.getResult().intValue() != 40) {
				result.unblockSuccess(null);
				return null;
			}
			long size = DataUtil.Read32U.LE.read(buf, 16);
			long start = DataUtil.Read32U.LE.read(buf, 20);
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
			if (size == 0 && start == 0 && DataUtil.Read64.LE.read(buf, 0) == 0) {
				result.unblockSuccess(null);
				return null;
			}
			asyncDetect(data, io, pos+40, sectionIndex+1, nb_sections, dataSize, sections, buf, buffer, result);
			return null;
		});
		read.thenStart(task, true);
	}
	
}
