package net.lecousin.dataformat.core.adapters;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.model.ModelBlock;
import net.lecousin.framework.adapter.Adapter;

public class DataToModel implements Adapter<Data, ModelBlock> {

	@Override
	public boolean canAdapt(Data input) {
		return input != null;
	}

	@Override
	public ModelBlock adapt(Data input) throws Exception {
		DataFormat format = input.detectFormatSync();
		if (format == null)
			return null;
		return format.getModel(input).blockResult(0);
	}

	@Override
	public Class<Data> getInputType() { return Data.class; }

	@Override
	public Class<ModelBlock> getOutputType() { return ModelBlock.class; }

}
