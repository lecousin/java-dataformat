package net.lecousin.dataformat.image.bmp;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.image.ImageDataFormatInfo;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;

public class DIBInfo extends ImageDataFormatInfo {

	@LocalizedName(namespace="dataformat.image.bmp",key="Bits per pixel")
	public int bitsPerPixel;

	public static AsyncWork<DIBInfo,Exception> load(Data data, byte priority, int offset) {
		AsyncWork<DIBInfo,Exception> result = new AsyncWork<>();
		AsyncWork<?, Exception> open = data.openReadOnly(priority);
		open.listenAsync(new Task.Cpu<Void, NoException>("Read DIBInfo from " + data.getDescription(), priority) {
			@SuppressWarnings("resource")
			@Override
			public Void run() {
				if (open.hasError()) { result.error(open.getError()); return null; }
				if (open.isCancelled()) { result.cancel(open.getCancelEvent()); return null; }
				byte[] header = new byte[124];
				IO.Readable.Buffered io = (IO.Readable.Buffered)open.getResult();
				try {
					if (offset > 0) io.skip(offset);
					io.readFully(header);
				} catch (IOException e) {
					result.error(new Exception("Error reading DIB header: " + e.getMessage(), e));
					return null;
				} finally {
					io.closeAsync();
				}
				DIBInfo info = new DIBInfo();
				if (header[0] == 12) {
					info.width = DataUtil.readUnsignedShortLittleEndian(header, 4);
					info.height = DataUtil.readUnsignedShortLittleEndian(header, 6);
					info.bitsPerPixel = DataUtil.readUnsignedShortLittleEndian(header, 10);
				} else {
					info.width = DataUtil.readIntegerLittleEndian(header, 4);
					info.height = DataUtil.readIntegerLittleEndian(header, 8);
					info.bitsPerPixel = DataUtil.readUnsignedShortLittleEndian(header, 14);
				}
				result.unblockSuccess(info);
				return null;
			}
		}, true);
		return result;
	}
	
}
