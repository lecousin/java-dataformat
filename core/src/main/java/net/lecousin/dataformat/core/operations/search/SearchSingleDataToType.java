package net.lecousin.dataformat.core.operations.search;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.operations.DataFormatReadOperation;
import net.lecousin.dataformat.core.operations.DataFormatWriteOperation;
import net.lecousin.dataformat.core.operations.DataOperationsRegistry;
import net.lecousin.dataformat.core.operations.DataToDataOperation;
import net.lecousin.dataformat.core.operations.Operation;
import net.lecousin.dataformat.core.operations.chain.DataFormatReadOperationOneToOneWithConversion;
import net.lecousin.dataformat.core.operations.chain.DataFormatReadOperationOneToOneWithIntermediates;
import net.lecousin.dataformat.core.operations.chain.DataToDataOperationOneToOneReadThenWrite;

public final class SearchSingleDataToType {
	
	private SearchSingleDataToType() {
		// no instance
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <Input extends DataFormat, Output> DataFormatReadOperation.OneToOne<Input, Output, ?> searchOneToOnePathToType(Input sourceFormat, Class<Output> targetType) {
		DataFormatReadOperation.OneToOne<Input, Output, ?> res = searchOneToOnePathToTypeWithoutConversion(sourceFormat, targetType);
		if (res != null)
			return res;
		// check if we may convert the data into a different format, and find a path
		List<DataToDataOperation.OneToOne<?, ?>> conversions = searchOneToOneConversions(sourceFormat);
		for (DataToDataOperation.OneToOne<?, ?> conversion : conversions) {
			DataFormatReadOperation.OneToOne<?, Output, ?> read = searchOneToOnePathToTypeWithoutConversion(conversion.getOutputFormat(), targetType);
			if (read != null)
				return new DataFormatReadOperationOneToOneWithConversion(sourceFormat, conversion, read);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <Input extends DataFormat, Output> DataFormatReadOperation.OneToOne<Input, Output, ?> searchOneToOnePathToTypeWithoutConversion(Input sourceFormat, Class<Output> targetType) {
		// we need first a read operation
		ArrayList<DataFormatReadOperation.OneToOne<Input,?,?>> readOne = new ArrayList<>();
		ArrayList<Class<?>> readOneResult = new ArrayList<>();
		for (DataFormatReadOperation.OneToOne<?,?,?> op : DataOperationsRegistry.oneToOneReadOperations) {
			if (op.getInputFormat() != sourceFormat) continue;
			if (targetType.isAssignableFrom(op.getOutputType()))
				return (DataFormatReadOperation.OneToOne<Input, Output, ?>)op; // we have the exact operation, take it
			readOne.add((DataFormatReadOperation.OneToOne<Input,?,?>)op);
			readOneResult.add(op.getOutputType());
		}
		// we don't have the exact read operation, search path from the different intermediates
		for (int i = 0; i < readOne.size(); ++i) {
			Class<?> intermediate = readOneResult.get(i);
			Operation.OneToOne<?,Output,?> op = SearchTypeToType.searchOneToOnePath(intermediate, targetType, readOneResult);
			if (op != null)
				return new DataFormatReadOperationOneToOneWithIntermediates<Input,Output>(readOne.get(0), op);
		}
		// TODO try with OneToMany
		return null;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static List<DataToDataOperation.OneToOne<?, ?>> searchOneToOneConversions(DataFormat sourceFormat) {
		List<DataToDataOperation.OneToOne<?, ?>> conversions = new LinkedList<>();
		List<DataFormat> formatsFound = new LinkedList<>();
		// first, we have the data to data operations
		for (DataToDataOperation.OneToOne<?, ?> op : DataOperationsRegistry.oneToOneDataToDataOperations) {
			if (formatsFound.contains(op.getOutputFormat()))
				continue;
			List<Class<? extends DataFormat>> accepted = op.getAcceptedInputs();
			boolean accept;
			if (accepted == null || accepted.isEmpty())
				accept = true;
			else {
				accept = false;
				for (Class<? extends DataFormat> cl : accepted)
					if (cl.isAssignableFrom(sourceFormat.getClass())) {
						accept = true;
						break;
					}
			}
			if (accept) {
				conversions.add(op);
				formatsFound.add(op.getOutputFormat());
			}
		}
		// then we try to find paths using intermediate objects
		for (DataFormatWriteOperation.OneToOne<?,?,?> op : DataOperationsRegistry.dataFormatOneToOneWriteOperations) {
			if (formatsFound.contains(op.getOutputFormat()))
				continue;
			DataFormatReadOperation.OneToOne<?,?,?> path = searchOneToOnePathToTypeWithoutConversion(sourceFormat, op.getInputType());
			if (path != null) {
				conversions.add(new DataToDataOperationOneToOneReadThenWrite(path, op));
				formatsFound.add(op.getOutputFormat());
			}
		}
		// TODO many to one ?
		// TODO combine with new conversions to target new types
		return conversions;
	}
	
}
