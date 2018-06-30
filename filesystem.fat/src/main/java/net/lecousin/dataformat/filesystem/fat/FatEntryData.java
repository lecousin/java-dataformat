package net.lecousin.dataformat.filesystem.fat;

import java.util.ArrayList;

import net.lecousin.dataformat.core.Data;
import net.lecousin.framework.collections.AsyncCollection;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.IO.Readable.Seekable;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableStringBuffer;
import net.lecousin.framework.mutable.MutableLong;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;

public class FatEntryData extends Data {

	FatEntryData(Data container, FAT fat, FatEntry entry) {
		this.container = container;
		this.fat = fat;
		this.entry = entry;
		if (entry.isDirectory())
			setFormat(FATDirectoryDataFormat.instance);
	}
	
	private Data container;
	private FAT fat;
	private FatEntry entry;
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString(entry.getName());
	}
	
	@Override
	public ILocalizableString getDescription() {
		return new LocalizableStringBuffer(container.getDescription(), "/" + getName());
	}
	
	@Override
	public long getSize() {
		return entry.size;
	}
	
	@Override
	public boolean hasContent() {
		return true;
	}
	
	@Override
	public Data getContainer() {
		return container;
	}
	
	@Override
	protected AsyncWork<Readable, ? extends Exception> openIOReadOnly(byte priority) {
		return new AsyncWork<>(new FatEntryIO(fat, entry, priority), null);
	}

	@Override
	protected boolean canOpenReadWrite() {
		return false;
	}
	@Override
	protected <T extends Seekable & net.lecousin.framework.io.IO.Writable.Seekable> AsyncWork<T, ? extends Exception> openIOReadWrite(byte priority) {
		return null;
	}
	
	WorkProgress listDirectoryContent(CollectionListener<Data> listener) {
		WorkProgress progress = new WorkProgressImpl(1000, "Reading FAT Directory");
		listener.elementsReady(new ArrayList<>(0));
		MutableLong work = new MutableLong(1000);
		fat.readDirectory(entry.cluster, new AsyncCollection.Listen<>(
			(elements) -> {
				ArrayList<Data> list = new ArrayList<>(elements.size());
				for (FatEntry entry : elements)
					list.add(new FatEntryData(FatEntryData.this, fat, entry));
				listener.elementsAdded(list);
				progress.progress(work.get() / 3);
				work.set(work.get() - work.get() / 3);
			},
			() -> {
				progress.done();
			},
			(error) -> {
				listener.error(error);
				progress.error(error);
			}
		));
		return progress;
	}
	
}
