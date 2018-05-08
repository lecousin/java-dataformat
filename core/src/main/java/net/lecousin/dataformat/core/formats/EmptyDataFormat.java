package net.lecousin.dataformat.core.formats;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.dataformat.core.actions.InitNewDataAction;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.ui.iconset.IconSet;
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
	public AsyncWork<DataFormatInfo,Exception> getInfo(Data data, byte priority) {
		return null;
	}
	
	@Override
	public IconProvider getIconProvider() {
		return IconSet.getIcon(FilesIconSet.class, FilesIconSet.ICON_BLANK_FILE);
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
		public ISynchronizationPoint<NoException> execute(Data data, IO.Writable io, Void params, WorkProgress progress, long work) {
			return new SynchronizationPoint<>(true);
		}
		
	};
	
	@Override
	public InitNewDataAction<?, ?> getInitNewDataAction() {
		return newEmptyDataAction;
	}
	
}
