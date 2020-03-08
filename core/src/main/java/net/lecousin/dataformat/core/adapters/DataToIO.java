package net.lecousin.dataformat.core.adapters;

import java.io.IOException;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.adapter.Adapter;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO;

public class DataToIO {

	public static class Readable implements Adapter<Data,IO.Readable> {
		@Override
		public Class<Data> getInputType() { return Data.class; }
		@Override
		public Class<IO.Readable> getOutputType() { return IO.Readable.class; }
		@Override
		public boolean canAdapt(Data input) {
			return input.hasContent();
		}
		@Override
		public IO.Readable adapt(Data input) {
			AsyncSupplier<? extends IO.Readable.Seekable, IOException> sp = input.openReadOnly(Priority.NORMAL);
			sp.block(0);
			return sp.getResult();
		}

		public static class Seekable implements Adapter<Data,IO.Readable.Seekable> {
			@Override
			public Class<Data> getInputType() { return Data.class; }
			@Override
			public Class<IO.Readable.Seekable> getOutputType() { return IO.Readable.Seekable.class; }
			@Override
			public boolean canAdapt(Data input) {
				return input.hasContent();
			}
			@Override
			public IO.Readable.Seekable adapt(Data input) {
				AsyncSupplier<? extends IO.Readable.Seekable, IOException> sp = input.openReadOnly(Priority.NORMAL);
				sp.block(0);
				IO.Readable io = sp.getResult();
				return (IO.Readable.Seekable)io;
			}
		}
	
	}
	
}
