package net.lecousin.dataformat.model;

import java.math.BigInteger;

import net.lecousin.framework.io.IO;

public class ModelIntegerVariableCalculation extends ModelVariable<BigInteger> {

	public ModelIntegerVariableCalculation(ModelBlock parent, String name) {
		super(parent, name);
	}
	
	@Override
	public BigInteger getValue(IO.Readable.Seekable io) throws Exception {
		// TODO
		return BigInteger.ZERO;
	}
	
}
