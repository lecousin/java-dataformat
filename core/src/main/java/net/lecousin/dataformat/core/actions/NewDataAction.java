package net.lecousin.dataformat.core.actions;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.actions.NewDataAction.NewDataParam;
import net.lecousin.dataformat.core.formats.EmptyDataFormat;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;

@SuppressWarnings("rawtypes")
public class NewDataAction implements DataAction.SingleData<NewDataParam, Data, Exception> {

	public static class NewDataParam {
		
		public DataFormat format = EmptyDataFormat.instance;
		
		public Object createParams;
		
		public Object initParams = format.getInitNewDataAction().createParameters();
		
	}
	
	public NewDataAction(CreateDataAction createAction) {
		this.createAction = createAction;
	}
	
	protected CreateDataAction createAction;

	@Override
	public ILocalizableString getName() { return createAction.getName(); }

	@Override
	public IconProvider iconProvider() { return createAction.iconProvider(); }

	@Override
	public NewDataParam createParameter(Data data) {
		NewDataParam p = new NewDataParam();
		p.createParams = createAction.createParameter(data);
		return p;
	}

	@SuppressWarnings("unchecked")
	@Override
	public AsyncWork<Data, Exception> execute(Data data, NewDataParam parameter, byte priority, WorkProgress progress, long work) {
		AsyncWork<Data, Exception> result = new AsyncWork<>();
		long stepCreate = work / 3;
		long stepInit = work - stepCreate;
		createAction.execute(data, parameter.createParams, priority, progress, stepCreate)
		.listenInline((res) -> {
			Pair<Data, IO.Writable> p = (Pair<Data, IO.Writable>)res;
			Data newData = p.getValue1();
			@SuppressWarnings("resource")
			IO.Writable io = p.getValue2();
			((InitNewDataAction)parameter.format.getInitNewDataAction()).execute(newData, io, parameter.initParams, progress, stepInit)
			.listenInline(() -> {
				io.closeAsync();
				result.unblockSuccess(newData);
				progress.done();
			}, result);
		}, result);
		return result;
	}
	
	
	
}
