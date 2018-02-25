package net.lecousin.dataformat.core.operations.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lecousin.dataformat.core.operations.DataOperationsRegistry;
import net.lecousin.dataformat.core.operations.Operation;
import net.lecousin.dataformat.core.operations.chain.OperationOneToOneChain;

public final class SearchTypeToType {
	
	private SearchTypeToType() {
		// no instance
	}

	@SuppressWarnings("unchecked")
	public static <Input,Output> Operation.OneToOne<Input,Output,?> searchOneToOnePath(Class<Input> source, Class<Output> target, List<Class<?>> except) {
		// first, we search a direct intermediate operation
		Map<Class<?>,Operation.OneToOne<?,?,?>> intermediates = new HashMap<>();
		for (Operation.OneToOne<?,?,?> op : DataOperationsRegistry.oneToOneOperations) {
			if (!op.getInputType().isAssignableFrom(source)) continue;
			if (target.isAssignableFrom(op.getOutputType()))
				return (Operation.OneToOne<Input,Output,?>)op;
			if (!except.contains(op.getOutputType()) && !intermediates.containsKey(op.getOutputType()))
				intermediates.put(op.getOutputType(), op);
		}
		for (Operation.ManyToOne<?,?,?> op : DataOperationsRegistry.manyToOneOperations) {
			if (!op.getInputType().isAssignableFrom(source)) continue;
			if (target.isAssignableFrom(op.getOutputType()))
				return new Operation.ManyToOneAsOneToOne(op);
			if (!except.contains(op.getOutputType()) && !intermediates.containsKey(op.getOutputType()))
				intermediates.put(op.getOutputType(), new Operation.ManyToOneAsOneToOne(op));
		}
		// no direct operation, continue to search intermediate operations
		Map<Class<?>,Operation.OneToOne<?,?,?>> newNodes = new HashMap<>(intermediates);
		do {
			Map<Class<?>,Operation.OneToOne<?,?,?>> startNodes = newNodes;
			newNodes = new HashMap<>();
			for (Map.Entry<Class<?>,Operation.OneToOne<?,?,?>> e : startNodes.entrySet()) {
				for (Operation.OneToOne<?,?,?> op : DataOperationsRegistry.oneToOneOperations) {
					if (!op.getInputType().isAssignableFrom(e.getKey())) continue;
					if (target.isAssignableFrom(op.getOutputType()))
						return OperationOneToOneChain.createRaw(e.getValue(), op);
					if (!except.contains(op.getOutputType()) && !intermediates.containsKey(op.getOutputType())) {
						intermediates.put(op.getOutputType(), op);
						newNodes.put(op.getOutputType(), op);
					}
				}
				for (Operation.ManyToOne<?,?,?> op : DataOperationsRegistry.manyToOneOperations) {
					if (!op.getInputType().isAssignableFrom(e.getKey())) continue;
					if (target.isAssignableFrom(op.getOutputType()))
						return OperationOneToOneChain.create(e.getValue(), new Operation.ManyToOneAsOneToOne(op));
					if (!except.contains(op.getOutputType()) && !intermediates.containsKey(op.getOutputType())) {
						Operation.ManyToOneAsOneToOne nop = new Operation.ManyToOneAsOneToOne(op);
						intermediates.put(op.getOutputType(), nop);
						newNodes.put(op.getOutputType(), nop);
					}
				}
			}
		} while (!newNodes.isEmpty());
		// TODO search with conversion
		return null;
	}
	
}
