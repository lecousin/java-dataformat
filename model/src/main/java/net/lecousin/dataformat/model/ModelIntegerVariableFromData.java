package net.lecousin.dataformat.model;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import net.lecousin.framework.io.IO;

public class ModelIntegerVariableFromData extends ModelVariable<BigInteger> implements IModelLocation {

	public ModelIntegerVariableFromData(ModelBlock parent, String name, long offset, byte length, boolean littleEndian) {
		super(parent, name);
		this.offset = offset;
		this.length = length;
		this.littleEndian = littleEndian;
	}
	
	protected long offset;
	protected byte length;
	protected boolean littleEndian;
	
	@Override
	public long getOffset() {
		return offset;
	}
	
	@Override
	public long getLength() {
		return length;
	}
	
	@Override
	public BigInteger getValue(IO.Readable.Seekable io) throws Exception {
		byte[] val = new byte[length];
		io.readFullySync(offset, ByteBuffer.wrap(val));
		if (littleEndian) {
			byte[] tmp = new byte[length];
			for (int i = 0; i < length; ++i)
				tmp[i] = val[length - i - 1];
			val = tmp;
		}
		return new BigInteger(val);
	}
	
}
