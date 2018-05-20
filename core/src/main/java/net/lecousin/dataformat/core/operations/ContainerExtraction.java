package net.lecousin.dataformat.core.operations;

import java.util.Collection;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.actions.CreateContainerDataAction;
import net.lecousin.dataformat.core.actions.CreateDataAction;
import net.lecousin.dataformat.core.hierarchy.IDirectoryData;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.collections.LinkedArrayList;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.util.Pair;

public class ContainerExtraction {

	public static WorkProgress extract(Data container, Data target) {
		// TODO localize
		WorkProgressImpl progress = new WorkProgressImpl(1000000, "Extracting data from " + container.getName());
		// check container
		DataFormat format = container.getDetectedFormat();
		if (format == null) {
			progress.error(new Exception("Cannot extract from a data which has no format"));
			return progress;
		}
		if (!(format instanceof ContainerDataFormat)) {
			progress.error(new Exception("Cannot extract from a data which does not contain sub-data"));
			return progress;
		}
		// check target
		DataFormat targetFormat = target.getDetectedFormat();
		if (targetFormat == null) {
			progress.error(new Exception("Cannot extract to a data which has no format"));
			return progress;
		}
		if (!(targetFormat instanceof ContainerDataFormat)) {
			progress.error(new Exception("Cannot extract to a data which cannot contain sub-data"));
			return progress;
		}

		// listing data
		progress.setSubText("Listing files to extract");
		Dir root = new Dir();
		ISynchronizationPoint<Exception> list = listData((ContainerDataFormat)format, container, root);
		list.listenAsync(new Task.Cpu.FromRunnable("Extract files", Task.PRIORITY_NORMAL, () -> {
			if (list.hasError()) {
				progress.error(list.getError());
				return;
			}
			if (list.isCancelled()) {
				progress.cancel(list.getCancelEvent());
				return;
			}
			long amount = root.totalSize() + root.totalFiles() * 1024 + root.totalDirs() * 4096;
			progress.setAmount(amount + 65536);
			progress.progress(65536);
			extract(root, target, progress).listenInline(() -> { progress.done(); }, progress.getSynch());
		}), true);
		return progress;
	}
	
	private static class Dir {
		
		private ILocalizableString name;
		private LinkedArrayList<Data> files = new LinkedArrayList<>(10);
		private LinkedArrayList<Dir> dirs = new LinkedArrayList<>(10);
		
		private long totalSize() {
			long total = 0;
			for (Data file : files)
				total += file.getSize();
			for (Dir d : dirs)
				total += d.totalSize();
			return total;
		}
		
		private int totalFiles() {
			int total = files.size();
			for (Dir d : dirs) total += d.totalFiles();
			return total;
		}
		
		private int totalDirs() {
			int total = dirs.size();
			for (Dir d : dirs) total += d.totalDirs();
			return total;
		}
		
	}
	
	private static ISynchronizationPoint<Exception> listData(ContainerDataFormat containerFormat, Data container, Dir dir) {
		JoinPoint<Exception> jp = new JoinPoint<>();
		jp.addToJoin(1);
		containerFormat.listenSubData(container, new CollectionListener<Data>() {

			@Override
			public void elementsReady(Collection<? extends Data> elements) {
				containerFormat.unlistenSubData(container, this);
				for (Data data : elements) {
					if (data instanceof IDirectoryData) {
						Dir d = new Dir();
						d.name = data.getName();
						jp.addToJoin(listData(data, d));
						dir.dirs.add(d);
					} else
						dir.files.add(data);
				}
				jp.joined();
			}
			
			@Override
			public void elementsAdded(Collection<? extends Data> elements) {
			}

			@Override
			public void elementsRemoved(Collection<? extends Data> elements) {
			}

			@Override
			public void elementsChanged(Collection<? extends Data> elements) {
			}

			@Override
			public void error(Throwable error) {
				if (error instanceof Exception)
					jp.error((Exception)error);
				else
					jp.error(new Exception(error));
			}
		});
		jp.start();
		return jp;
	}
	
	private static ISynchronizationPoint<Exception> listData(Data data, Dir dir) {
		AsyncWork<DataFormat, Exception> detect = data.detectFinalFormat(Task.PRIORITY_NORMAL, null, 0);
		if (detect.isUnblocked()) {
			if (detect.hasError() || detect.isCancelled())
				return detect;
			if (!(detect.getResult() instanceof ContainerDataFormat))
				return new SynchronizationPoint<>(new Exception("Unexpected format for " + data.getDescription()));
			return listData((ContainerDataFormat)detect.getResult(), data, dir);
		}
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		detect.listenAsync(new Task.Cpu.FromRunnable("List files to extract", Task.PRIORITY_NORMAL, () -> {
			if (!(detect.getResult() instanceof ContainerDataFormat))
				sp.error(new Exception("Unexpected format for " + data.getDescription()));
			else
				listData((ContainerDataFormat)detect.getResult(), data, dir).listenInline(sp);
		}), sp);
		return sp;
	}
	
	private static ISynchronizationPoint<Exception> extract(Dir dir, Data target, WorkProgress progress) {
		if (progress.getSynch().isUnblocked())
			return progress.getSynch();

		JoinPoint<Exception> jp = new JoinPoint<>();
		for (Data srcFile : dir.files)
			jp.addToJoin(extract(srcFile, target, progress));
		for (Dir srcDir : dir.dirs)
			jp.addToJoin(createDir(srcDir, target, progress));
		jp.start();
		return jp;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static ISynchronizationPoint<Exception> createDir(Dir dir, Data target, WorkProgress progress) {
		if (progress.getSynch().isUnblocked())
			return progress.getSynch();
		
		CreateContainerDataAction<?,?> createContainer = ((ContainerDataFormat)target.getDetectedFormat()).getCreateNewContainerDataAction(target);
		if (createContainer == null) {
			Exception error = new Exception("Unable to create sub-directory in " + target.getDescription());
			progress.error(error);
			return new SynchronizationPoint<>(error);
		}
		
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		CreateContainerDataAction.Param param = createContainer.createParameter(target);
		dir.name.appLocalization().listenInline((localizedName) -> {
			param.setName(localizedName);
			AsyncWork<Data, Exception> result = ((CreateContainerDataAction)createContainer).execute(target, param, Task.PRIORITY_NORMAL, progress, 4096L);
			result.listenAsync(new Task.Cpu.FromRunnable("Extract files", Task.PRIORITY_NORMAL, () -> {
				Data d = result.getResult();
				DataFormat f = d.getDetectedFormat();
				if (!(f instanceof ContainerDataFormat)) {
					Exception error = new Exception("Created directory has no container format set: " + d.getDescription());
					sp.error(error);
					progress.error(error);
					return;
				}
				extract(dir, d, progress).listenInline(sp);
			}), sp);
		});
		return sp;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked", "resource" })
	private static ISynchronizationPoint<Exception> extract(Data src, Data targetDir, WorkProgress progress) {
		if (progress.getSynch().isUnblocked())
			return progress.getSynch();
		
		CreateDataAction<?,?> createData = ((ContainerDataFormat)targetDir.getDetectedFormat()).getCreateNewDataAction(targetDir);
		if (createData == null) {
			Exception error = new Exception("Unable to create file in " + targetDir.getDescription());
			progress.error(error);
			return new SynchronizationPoint<>(error);
		}
		
		JoinPoint<Exception> jp = new JoinPoint<>();
		SynchronizationPoint<Exception> sp = new SynchronizationPoint<>();
		AsyncWork<? extends IO.Readable, Exception> open = src.openReadOnly(Task.PRIORITY_NORMAL);
		jp.addToJoin(open);
		src.getName().appLocalization().listenInline((localizedName) -> {
			CreateDataAction.Param param = createData.createParameter(targetDir);
			param.setName(localizedName);
			AsyncWork<Pair<Data, IO.Writable>, Exception> create = ((CreateDataAction)createData).execute(targetDir, param, Task.PRIORITY_NORMAL, progress, 1024);
			jp.addToJoin(create);
			jp.listenInline(() -> {
				if (open.isSuccessful())
					open.getResult().closeAsync();
				if (create.isSuccessful())
					create.getResult().getValue2().closeAsync();
			});
			jp.addToJoin(1);
			jp.start();
			jp.listenAsync(new Task.Cpu.FromRunnable("Extract file " + src.getName(), Task.PRIORITY_NORMAL, () -> {
				IO.Writable out = create.getResult().getValue2();
				IO.Readable in = open.getResult();
				jp.addToJoin(IOUtil.copy(in, out, src.getSize(), false, progress, src.getSize()));
				jp.joined();
			}), sp);
		});
		return sp;
	}
	
}
