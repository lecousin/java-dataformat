package net.lecousin.dataformat.core.formats;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.actions.InitNewDataAction;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui.iconset.files.FilesIconSet;
import net.lecousin.framework.uidescription.resources.IconProvider;

public class EmptyDataFormat implements DataFormat {

	public static EmptyDataFormat instance = new EmptyDataFormat();
	
	private EmptyDataFormat() {}
	
	@Override
	public LocalizableString getName() {
		return new LocalizableString("dataformat", "Empty");
	}
	
	@Override
	public AsyncSupplier<DataFormatInfo,Exception> getInfo(Data data, Priority priority) {
		return null;
	}
	
	@Override
	public IconProvider getIconProvider() {
		return FilesIconSet.Icons.BLANK_FILE.get();
	}

	public static final String[] nothing = new String[0];
	@Override
	public String[] getFileExtensions() {
		return nothing;
	}
	@Override
	public String[] getMIMETypes() {
		return nothing;
	}
	
	public static final InitNewDataAction<Void, NoException> newEmptyDataAction = new InitNewDataAction<Void, NoException>() {

		@Override
		public Void createParameters() {
			return null;
		}

		@Override
		public IAsync<NoException> execute(Data data, IO.Writable io, Void params, WorkProgress progress, long work) {
			return new Async<>(true);
		}
		
	};
	
	@Override
	public InitNewDataAction<?, ?> getInitNewDataAction() {
		return newEmptyDataAction;
	}
	
}
