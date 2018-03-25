package net.lecousin.dataformat.core.operations;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.dataformat.core.DataFormat;

public class DataOperationsRegistry {

	public static ArrayList<DataFormatReadOperation.OneToOne<?,?,?>> oneToOneReadOperations = new ArrayList<>();
	public static ArrayList<DataFormatReadOperation.OneToMany<?,?,?>> oneToManyReadOperations = new ArrayList<>();
	
	public static ArrayList<DataFormatWriteOperation.OneToOne<?,?,?>> dataFormatOneToOneWriteOperations = new ArrayList<>();
	public static ArrayList<DataFormatWriteOperation.ManyToOne<?,?,?>> dataFormatManyToOneWriteOperations = new ArrayList<>();
	
	public static ArrayList<Operation.OneToOne<?,?,?>> oneToOneOperations = new ArrayList<>();
	public static ArrayList<Operation.OneToMany<?,?,?>> oneToManyOperations = new ArrayList<>();
	public static ArrayList<Operation.ManyToOne<?,?,?>> manyToOneOperations = new ArrayList<>();
	public static ArrayList<Operation.ManyToMany<?,?,?>> manyToManyOperations = new ArrayList<>();
	
	public static ArrayList<TypeOperation<?,?>> typeOperations = new ArrayList<>();
	
	public static ArrayList<DataToDataOperation.OneToOne<?, ?>> oneToOneDataToDataOperations = new ArrayList<>();
	public static ArrayList<DataToDataOperation.ManyToOne<?, ?>> manyToOneDataToDataOperations = new ArrayList<>();
	
	public static synchronized void register(IOperation<?> operation) {
		if (operation instanceof DataFormatReadOperation.OneToOne)
			oneToOneReadOperations.add((DataFormatReadOperation.OneToOne<?,?,?>)operation);
		else if (operation instanceof DataFormatReadOperation.OneToMany)
			oneToManyReadOperations.add((DataFormatReadOperation.OneToMany<?,?,?>)operation);
		else if (operation instanceof DataFormatWriteOperation.OneToOne)
			dataFormatOneToOneWriteOperations.add((DataFormatWriteOperation.OneToOne<?,?,?>)operation);
		else if (operation instanceof DataFormatWriteOperation.ManyToOne)
			dataFormatManyToOneWriteOperations.add((DataFormatWriteOperation.ManyToOne<?,?,?>)operation);
		else if (operation instanceof TypeOperation)
			typeOperations.add((TypeOperation<?,?>)operation);
		else if (operation instanceof Operation.OneToOne)
			oneToOneOperations.add((Operation.OneToOne<?,?,?>)operation);
		else if (operation instanceof Operation.OneToMany)
			oneToManyOperations.add((Operation.OneToMany<?,?,?>)operation);
		else if (operation instanceof Operation.ManyToOne)
			manyToOneOperations.add((Operation.ManyToOne<?,?,?>)operation);
		else if (operation instanceof Operation.ManyToMany)
			manyToManyOperations.add((Operation.ManyToMany<?,?,?>)operation);
		else if (operation instanceof DataToDataOperation.OneToOne)
			oneToOneDataToDataOperations.add((DataToDataOperation.OneToOne<?,?>)operation);
		else if (operation instanceof DataToDataOperation.ManyToOne)
			manyToOneDataToDataOperations.add((DataToDataOperation.ManyToOne<?,?>)operation);
		else
			throw new RuntimeException("Unknown operation type: " + operation.getClass().getName());
	}
	
	
	@SuppressWarnings("unchecked")
	public static <T extends DataFormat> List<DataFormatReadOperation.OneToOne<T,?,?>> getOneToOneReadOperationsFor(T format) {
		List<DataFormatReadOperation.OneToOne<T,?,?>> list = new LinkedList<>();
		for (DataFormatReadOperation.OneToOne<?,?,?> op : oneToOneReadOperations) {
			if (op.getInputFormat().equals(format))
				list.add((DataFormatReadOperation.OneToOne<T,?,?>)op);
		}
		return list;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> List<DataFormatWriteOperation.OneToOne<T, ?, ?>> getOneToOneWriteOperationsFor(Class<T> type) {
		List<DataFormatWriteOperation.OneToOne<T, ?, ?>> list = new LinkedList<>();
		for (DataFormatWriteOperation.OneToOne<?, ?, ?> op : dataFormatOneToOneWriteOperations)
			if (op.getInputType().isAssignableFrom(type))
				list.add((DataFormatWriteOperation.OneToOne<T, ?, ?>)op);
		return list;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> List<Operation.OneToOne<T, ?, ?>> getOneToOneOperationsFor(Class<T> type) {
		List<Operation.OneToOne<T, ?, ?>> list = new LinkedList<>();
		for (Operation.OneToOne<?, ?, ?> op : oneToOneOperations)
			if (op.getInputType().isAssignableFrom(type))
				list.add((Operation.OneToOne<T, ?, ?>)op);
		return list;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> List<TypeOperation<T,?>> getTypeOperations(Class<T> type) {
		List<TypeOperation<T,?>> list = new LinkedList<>();
		for (TypeOperation<?,?> op : typeOperations)
			if (op.getType().equals(type))
				list.add((TypeOperation<T,?>)op);
		return list;
	}
	
	
	/*
	public static AsyncWork<TODO,Exception> searchConversions(List<Data> inputs, byte priority) {
		// first, we need to ensure we know the format of each data
		JoinPoint<Exception> jp = new JoinPoint<>();
		jp.addToJoin(inputs.size());
		for (Data data : inputs)
			data.detect(priority, null, 0, new DataFormatListener() {
				@Override
				public void formatDetected(Data data, DataFormat format) {
				}
				@Override
				public void endOfDetection(Data data) {
					jp.joined();
				}
				@Override
				public void detectionError(Data data, Exception error) {
					if (!jp.hasError())
						jp.error(new Exception("Unable to detect format for " + data.getName(), error));
				}
				
				@Override
				public void detectionCancelled(Data data) {
					jp.cancel(new CancelException("Detection cancelled for " + data.getName()));
				}
			}, new SingleEvent<>());
		AsyncWork<TODO,Exception> result = new AsyncWork<>();
		jp.start();
		jp.listenAsynch(new Task.Cpu<Void,NoException>("Search possible data conversions", priority) {
			@Override
			public Void run() {
				if (jp.hasError()) { result.error(jp.getError()); return null; }
				if (jp.isCancelled()) { result.cancel(jp.getCancelEvent()); return null; }
				TODO tree = searchConversions(inputs);
				result.unblockSuccess(tree);
				return null;
			}
		}, true);
		return result;
	}
	
	private static Map<DataFormat, TODO> searchConversions(List<Data> inputs) {
	}
	*/
	
	/*
	@SuppressWarnings("unchecked")
	public static <Type> ArrayList<TypeOperation<Type,?>> getTypeOperations(Class<Type> type) {
		ArrayList<TypeOperation<Type,?>> list = new ArrayList<>();
		synchronized (typeOperations) {
			for (TypeOperation<?,?> op : typeOperations) {
				if (op.getInputType().isAssignableFrom(type))
					list.add((TypeOperation<Type,?>)op);
			}
		}
		return list;
	}
	
	public static <Input extends DataFormat, Output> DataFormatReadOperation.OneToOne<Input,Output,?> searchDataFormatOneToOneReadOperation(Input input, Class<Output> output) {
		return searchDataFormatOneToOneReadOperation(input, output, new ArrayList<>());
	}
	@SuppressWarnings("unchecked")
	private static <Input extends DataFormat, Output> DataFormatReadOperation.OneToOne<Input,Output,?> searchDataFormatOneToOneReadOperation(Input input, Class<Output> output, ArrayList<DataFormat> exceptIntermediateFormats) {
		ArrayList<DataFormatReadOperation.OneToOne<Input,Object,?>> possibleOutputs = new ArrayList<>();
		synchronized (oneToOneReadOperations) {
			for (DataFormatReadOperation.OneToOne<?,?,?> op : oneToOneReadOperations) {
				DataFormat in = op.getInputFormat();
				if (!in.getClass().isAssignableFrom(input.getClass())) continue;
				Class<?> out = op.getOutputType();
				if (output.isAssignableFrom(out))
					return (DataFormatReadOperation.OneToOne<Input,Output,?>)op;
				possibleOutputs.add((DataFormatReadOperation.OneToOne<Input,Object,?>)op);
			}
		}
		// check if we can find operations to convert to the target format
		for (DataFormatReadOperation.OneToOne<Input,Object,?> op : possibleOutputs) {
			Operation.OneToOne<Object,Output,?> op2 = searchOneToOneOperation(op.getOutputType(), output);
			if (op2 != null)
				return DataOperationsChain.create(op, op2);
		}
		// check if we can find intermediate data format
		for (DataFormatWriteOperation.OneToOne<?,? extends DataFormat,?> writeOp : dataFormatOneToOneWriteOperations) {
			DataFormat target = writeOp.getOutputFormat();
			if (exceptIntermediateFormats.contains(target))
				continue;
			Class<?> neededInput = writeOp.getInputType();
			DataFormatReadOperation.OneToOne<Input,?,?> direct = null;
			DataFormatReadOperation.OneToOne<Input,?,?> indirect = null;
			for (DataFormatReadOperation.OneToOne<Input,Object,?> op : possibleOutputs) {
				if (neededInput.isAssignableFrom(op.getOutputType())) {
					direct = op;
					break;
				}
				if (indirect == null) {
					Operation.OneToOne<Object,?,?> op2 = searchOneToOneOperation(op.getOutputType(), neededInput);
					if (op2 != null)
						indirect = DataOperationsChain.create(op, op2);
				}
			}
			if (direct == null && indirect == null)
				continue;
			if (direct == null)
				direct = indirect;
			ArrayList<DataFormat> except = new ArrayList<>(exceptIntermediateFormats);
			except.add(input);
			DataFormatReadOperation.OneToOne<?,Output,?> next = searchDataFormatOneToOneReadOperation(target, output, except);
			if (next != null) {
				// TODO do not take the first one, but the one with less conversions
				return DataOperationsChain.create(input, output, direct, writeOp, next);
			}
		}
		return null;
	}
	
	public static <Input,Output> Operation.OneToOne<Input,Output,?> searchOneToOneOperation(Class<Input> input, Class<Output> output) {
		return searchOneToOneOperation(input, output, new ArrayList<>(0));
	}
	@SuppressWarnings("unchecked")
	private static <Input,Output> Operation.OneToOne<Input,Output,?> searchOneToOneOperation(Class<Input> input, Class<Output> output, ArrayList<Class<?>> typesUsed) {
		ArrayList<Operation.OneToOne<Input,Object,?>> possibleOutputs = new ArrayList<>();
		synchronized (oneToOneOperations) {
			for (Operation.OneToOne<?,?,?> op : oneToOneOperations) {
				Class<?> in = op.getInputType();
				if (!in.isAssignableFrom(input)) continue;
				Class<?> out = op.getOutputType();
				if (output.isAssignableFrom(out))
					return (Operation.OneToOne<Input,Output,?>)op;
				if (typesUsed.contains(out))
					continue;
				possibleOutputs.add((Operation.OneToOne<Input,Object,?>)op);
			}
		}
		for (Operation.OneToOne<Input,Object,?> op : possibleOutputs) {
			ArrayList<Class<?>> newList = new ArrayList<>(typesUsed.size()+1);
			newList.add(op.getInputType());
			Operation.OneToOne<Object,Output,?> op2 = searchOneToOneOperation(op.getOutputType(), output, newList);
			if (op2 != null)
				return DataOperationsChain.create(op, op2);
		}
		return null;
	}

	
	@SuppressWarnings("unchecked")
	public static <Output> DataFormatReadOperation.OneToMany<?,Output,?> searchDataFormatOneToManyReadOperation(DataFormat input, Class<Output> output) {
		synchronized (oneToManyReadOperations) {
			for (DataFormatReadOperation.OneToMany<?,?,?> op : oneToManyReadOperations) {
				Class<?> out = op.getOutputType();
				if (!output.isAssignableFrom(out)) continue;
				DataFormat in = op.getInputFormat();
				if (!in.getClass().isAssignableFrom(input.getClass())) continue;
				return (DataFormatReadOperation.OneToMany<?,Output,?>)op;
			}
		}
		return null;
	}
	
	public static boolean hasReadOperation(DataFormat input) {
		synchronized (oneToOneReadOperations) {
			for (DataFormatReadOperation.OneToOne<?,?,?> op : oneToOneReadOperations) {
				DataFormat in = op.getInputFormat();
				if (in.getClass().isAssignableFrom(input.getClass()))
					return true;
			}
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public static <Input extends DataFormat> ArrayList<DataFormatReadOperation.OneToOne<Input,?,?>> getOneToOneReadOperations(Input input) {
		ArrayList<DataFormatReadOperation.OneToOne<Input,?,?>> list = new ArrayList<>();
		synchronized (oneToOneReadOperations) {
			for (DataFormatReadOperation.OneToOne<?,?,?> op : oneToOneReadOperations) {
				DataFormat in = op.getInputFormat();
				if (in.getClass().isAssignableFrom(input.getClass()))
					list.add((DataFormatReadOperation.OneToOne<Input,?,?>)op);
			}
		}
		return list;
	}
	@SuppressWarnings("unchecked")
	public static <Input extends DataFormat> ArrayList<DataFormatReadOperation.OneToMany<Input,?,?>> getOneToManyReadOperations(Input input) {
		ArrayList<DataFormatReadOperation.OneToMany<Input,?,?>> list = new ArrayList<>();
		synchronized (oneToManyReadOperations) {
			for (DataFormatReadOperation.OneToMany<?,?,?> op : oneToManyReadOperations) {
				DataFormat in = op.getInputFormat();
				if (in.getClass().isAssignableFrom(input.getClass()))
					list.add((DataFormatReadOperation.OneToMany<Input,?,?>)op);
			}
		}
		return list;
	}
	
	public static ArrayList<DataFormatWriteOperation.OneToOne<?,?,?>> getOneToOneWriteOperations(Class<?> input) {
		ArrayList<DataFormatWriteOperation.OneToOne<?,?,?>> list = new ArrayList<>();
		synchronized (dataFormatOneToOneWriteOperations) {
			for (DataFormatWriteOperation.OneToOne<?,?,?> op : dataFormatOneToOneWriteOperations) {
				if (op.getInputType().isAssignableFrom(input))
					list.add(op);
			}
		}
		return list;
	}
	
	@SuppressWarnings("unchecked")
	public static <Input extends DataFormat> ArrayList<Conversion<Input,?>> searchConversions(Input input) {
		ArrayList<Conversion<Input,?>> conversions = new ArrayList<>();
		// read one to one
		ArrayList<DataFormatReadOperation.OneToOne<Input,?,?>> readOne = getOneToOneReadOperations(input);
		for (DataFormatReadOperation.OneToOne<Input,?,?> op : readOne) {
			Class<?> intermediate = op.getOutputType();
			ArrayList<Pair<ArrayList<Operation<?,?,?>>,DataFormatWriteOperation>> paths = searchPathToNewFormatFromSingleObject(intermediate, input);
			for (Pair<ArrayList<Operation<?,?,?>>,DataFormatWriteOperation> path : paths) {
				conversions.add(new Conversion(op, path.getValue2(), path.getValue1().toArray(new Operation[path.getValue1().size()])));
			}
			// TODO continue
		}
		// TODO continue with read One to Many
		return conversions;
	}
	
	public static ArrayList<Pair<ArrayList<Operation<?,?,?>>,DataFormatWriteOperation>> searchPathToNewFormatFromSingleObject(Class<?> from, DataFormat except) {
		ArrayList<Pair<ArrayList<Operation<?,?,?>>,DataFormatWriteOperation>> paths = new ArrayList<>();
		// first, look on direct possible write
		ArrayList<DataFormatWriteOperation.OneToOne<?,?,?>> writeOne = getOneToOneWriteOperations(from);
		for (DataFormatWriteOperation.OneToOne<?,?,?> op : writeOne) {
			DataFormat out = op.getOutputFormat();
			if (out == except || except.getClass().isAssignableFrom(out.getClass()))
				continue;
			paths.add(new Pair<>(new ArrayList<Operation<?,?,?>>(0), op));
		}
		HashMap<Class<?>,ArrayList<Operation<?,?,?>>> singleOutputs = new HashMap<>();
		singleOutputs.put(from, null); // do not find a path going back to the input
		HashMap<Class<?>,ArrayList<Operation<?,?,?>>> multipleOutputs = new HashMap<>();
		multipleOutputs.put(from, null); // do not find a path going back to the input
		searchOperationsPathsFromOne(from, singleOutputs, multipleOutputs, new ArrayList<>(0));
		singleOutputs.remove(from);
		multipleOutputs.remove(from);
		for (Map.Entry<Class<?>,ArrayList<Operation<?,?,?>>> e : singleOutputs.entrySet()) {
			writeOne = getOneToOneWriteOperations(e.getKey());
			for (DataFormatWriteOperation.OneToOne<?,?,?> op : writeOne) {
				DataFormat out = op.getOutputFormat();
				if (out == except || except.getClass().isAssignableFrom(out.getClass()))
					continue;
				paths.add(new Pair<>(e.getValue(), op));
			}
			// TODO continue
		}
		for (Map.Entry<Class<?>,ArrayList<Operation<?,?,?>>> e : multipleOutputs.entrySet()) {
			writeOne = getOneToOneWriteOperations(e.getKey());
			for (DataFormatWriteOperation.OneToOne<?,?,?> op : writeOne) {
				DataFormat out = op.getOutputFormat();
				if (out == except || except.getClass().isAssignableFrom(out.getClass()))
					continue;
				paths.add(new Pair<>(e.getValue(), op));
			}
			// TODO continue
		}
		return paths;
	}
	
	private static void searchOperationsPathsFromOne(Class<?> from, Map<Class<?>,ArrayList<Operation<?,?,?>>> pathsToSingle, Map<Class<?>,ArrayList<Operation<?,?,?>>> pathsToMultiple, ArrayList<Operation<?,?,?>> currentPath) {
		// one to one
		synchronized (oneToOneOperations) {
			for (Operation.OneToOne<?,?,?> op : oneToOneOperations) {
				Class<?> input = op.getInputType();
				if (input == from || input.isAssignableFrom(from)) {
					Class<?> output = op.getOutputType();
					if (pathsToSingle.containsKey(output)) continue;
					// found a new possible output
					ArrayList<Operation<?,?,?>> path = new ArrayList<>(currentPath);
					path.add(op);
					pathsToSingle.put(output, path);
					// search for paths from this new output
					searchOperationsPathsFromOne(output, pathsToSingle, pathsToMultiple, path);
				}
			}
		}
		// one is a kind of many... let's see the many to one
		synchronized (manyToOneOperations) {
			for (Operation.ManyToOne<?,?,?> op : manyToOneOperations) {
				Class<?> input = op.getInputType();
				if (input == from || input.isAssignableFrom(from)) {
					Class<?> output = op.getOutputType();
					if (pathsToSingle.containsKey(output)) continue;
					// found a new possible output
					ArrayList<Operation<?,?,?>> path = new ArrayList<>(currentPath);
					path.add(op);
					pathsToSingle.put(output, path);
					// search for paths from this new output
					searchOperationsPathsFromOne(output, pathsToSingle, pathsToMultiple, path);
				}
			}
		}
		// one to many
		synchronized (oneToManyOperations) {
			for (Operation.OneToMany<?,?,?> op : oneToManyOperations) {
				Class<?> input = op.getInputType();
				if (input == from || input.isAssignableFrom(from)) {
					Class<?> output = op.getOutputType();
					if (pathsToMultiple.containsKey(output)) continue;
					// found a new possible output
					ArrayList<Operation<?,?,?>> path = new ArrayList<>(currentPath);
					path.add(op);
					pathsToMultiple.put(output, path);
					// search for paths from this new output
					searchOperationsPathsFromMany(output, pathsToSingle, pathsToMultiple, path);
				}
			}
		}
	}

	private static void searchOperationsPathsFromMany(Class<?> from, Map<Class<?>,ArrayList<Operation<?,?,?>>> pathsToSingle, Map<Class<?>,ArrayList<Operation<?,?,?>>> pathsToMultiple, ArrayList<Operation<?,?,?>> currentPath) {
		// many to one
		synchronized (manyToOneOperations) {
			for (Operation.ManyToOne<?,?,?> op : manyToOneOperations) {
				Class<?> input = op.getInputType();
				if (input == from || input.isAssignableFrom(from)) {
					Class<?> output = op.getOutputType();
					if (pathsToSingle.containsKey(output)) continue;
					// found a new possible output
					ArrayList<Operation<?,?,?>> path = new ArrayList<>(currentPath);
					path.add(op);
					pathsToSingle.put(output, path);
					// search for paths from this new output
					searchOperationsPathsFromOne(output, pathsToSingle, pathsToMultiple, path);
				}
			}
		}
		// many to many
		synchronized (manyToManyOperations) {
			for (Operation.ManyToMany<?,?,?> op : manyToManyOperations) {
				Class<?> input = op.getInputType();
				if (input == from || input.isAssignableFrom(from)) {
					Class<?> output = op.getOutputType();
					if (pathsToSingle.containsKey(output)) continue;
					if (pathsToMultiple.containsKey(output)) continue;
					// found a new possible output
					ArrayList<Operation<?,?,?>> path = new ArrayList<>(currentPath);
					path.add(op);
					pathsToMultiple.put(output, path);
					// search for paths from this new output
					searchOperationsPathsFromMany(output, pathsToSingle, pathsToMultiple, path);
				}
			}
		}
	}
	*/
}
