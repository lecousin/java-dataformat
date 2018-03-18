package net.lecousin.dataformat.core.actions;

import java.util.LinkedList;
import java.util.List;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.file.CreateFileAction;
import net.lecousin.dataformat.core.file.RemoveFilesAction;
import net.lecousin.dataformat.core.file.RenameFileAction;
import net.lecousin.framework.collections.LinkedArrayList;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.exception.NoException;

public final class DataActionsRegistry {

	private DataActionsRegistry() { /* no instance. */ }
	
	private static LinkedArrayList<DataAction> actions = new LinkedArrayList<>(20);
	
	static {
		register(new RenameFileAction());
		register(new RemoveFilesAction());
		register(new CreateFileAction());
	}
	
	public static void register(DataAction action) {
		actions.add(action);
	}
	
	public static List<DataAction> getAllActions() {
		return actions;
	}
	
	public static AsyncWork<List<DataAction.SingleData<?,?,?>>, NoException> getActions(Data data) {
		LinkedList<DataAction.SingleData<?,?,?>> list = new LinkedList<>();
		JoinPoint<NoException> jp = new JoinPoint<>();
		for (DataAction action : actions)
			if (action instanceof DataAction.SingleData) {
				AsyncWork<Boolean, NoException> can = ((DataAction.SingleData<?,?,?>)action).canExecute(data);
				if (can.isUnblocked()) {
					if (can.getResult().booleanValue())
						synchronized (list) {
							list.add((DataAction.SingleData<?,?,?>)action);
						}
				} else {
					jp.addToJoin(1);
					can.listenInline(() -> {
						if (can.isSuccessful() && can.getResult().booleanValue())
							synchronized (list) {
								list.add((DataAction.SingleData<?,?,?>)action);
							}
						jp.joined();
					});
				}
			}
		jp.start();
		AsyncWork<List<DataAction.SingleData<?,?,?>>, NoException> result = new AsyncWork<>();
		if (jp.isUnblocked())
			result.unblockSuccess(list);
		else
			jp.listenInline(() -> { result.unblockSuccess(list); });
		return result;
	}
	
	public static AsyncWork<List<DataAction.MultipleData<?,?,?>>, NoException> getActions(List<Data> dataList) {
		LinkedList<DataAction.MultipleData<?,?,?>> list = new LinkedList<>();
		JoinPoint<NoException> jp = new JoinPoint<>();
		for (DataAction action : actions) {
			if (!(action instanceof DataAction.MultipleData)) continue;
			AsyncWork<Boolean, NoException> can = ((DataAction.MultipleData<?,?,?>)action).canExecute(dataList);
			if (can.isUnblocked()) {
				if (can.getResult().booleanValue())
					synchronized (list) {
						list.add((DataAction.MultipleData<?,?,?>)action);
					}
			} else {
				jp.addToJoin(1);
				can.listenInline(() -> {
					if (can.isSuccessful() && can.getResult().booleanValue())
						synchronized (list) {
							list.add((DataAction.MultipleData<?,?,?>)action);
						}
					jp.joined();
				});
			}
		}
		jp.start();
		AsyncWork<List<DataAction.MultipleData<?,?,?>>, NoException> result = new AsyncWork<>();
		if (jp.isUnblocked())
			result.unblockSuccess(list);
		else
			jp.listenInline(() -> { result.unblockSuccess(list); });
		return result;
	}
	
}
