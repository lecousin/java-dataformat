package net.lecousin.dataformat.model;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import net.lecousin.framework.io.IO;

public class ModelStringVariableFromData extends ModelVariable<String> implements IModelLocation {

	public ModelStringVariableFromData(ModelBlock parent, String name, long offset, int length, Charset charset) {
		super(parent, name);
		this.offset = offset;
		this.length = length;
		this.charset = charset;
	}
	
	protected long offset;
	protected int length;
	protected Charset charset;
	
	@Override
	public long getOffset() {
		return offset;
	}
	
	@Override
	public long getLength() {
		return length;
	}
	
	@Override
	public String getValue(IO.Readable.Seekable io) throws Exception {
		byte[] buf = new byte[length];
		int nb = io.readFullySync(offset, ByteBuffer.wrap(buf));
		return new String(buf, 0, nb, charset);
	}
	
}
