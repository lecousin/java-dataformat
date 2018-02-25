package net.lecousin.dataformat.core.operations;

public interface DataOperationsChain {

	/*
	public static <Input extends DataFormat,Output>
	DataFormatReadOperation.OneToOne<Input,Output,?>
	create(
		Input input, Class<Output> output,
		DataFormatReadOperation.OneToOne<Input,?,?> read1,
		DataFormatWriteOperation.OneToOne<?, ? extends DataFormat, ?> write,
		DataFormatReadOperation.OneToOne<? extends DataFormat,Output,?> read2
	) {
		return new DataFormatReadOperationWithIntermediateFormat.OneToOne<Input,Output>(input, output, read1, write, read2);
	}
	
	public static class Conversion<Input extends DataFormat, Output extends DataFormat> {
		public Conversion(DataFormatReadOperation<Input, ?,?> read, DataFormatWriteOperation<?, Output,?> write, Operation<?, ?, ?>... intermediates) {
			this.read = read;
			this.write = write;
			this.intermediates = intermediates;
		}
		private DataFormatReadOperation<Input, ?,?> read;
		private DataFormatWriteOperation<?, Output,?> write;
		private Operation<?, ?, ?>[] intermediates;
		
		public DataFormat getOutputFormat() { return write.getOutputFormat(); }
		
		public DataFormatReadOperation<Input, ?,?> getInputOperation() { return read; }
		public DataFormatWriteOperation<?, Output,?> getOutputOperation() { return write; }
		public Operation<?, ?, ?>[] getIntermediateOperations() { return intermediates; }
		
		private List<Data> input;
		private Object readParameters;
		private OutputNameGenerator nameGenerator;
		private Provider.FromValue<String, AsyncWork<IO.Writable,IOException>> outputProvider;
		private Object writeParameters;
		private Object[] intermediateParameters;
		
		public static interface OutputNameGenerator {
			public String getOutputName(String originalName, List<Pair<Localized,String>> variables);
		}
		
		public void setInput(List<Data> input, Object readParameters) {
			this.input = input;
			this.readParameters = readParameters;
		}
		
		public void setOutput(OutputNameGenerator nameGenerator, Provider.FromValue<String, AsyncWork<IO.Writable,IOException>> outputProvider, Object writeParameters) {
			this.nameGenerator = nameGenerator;
			this.outputProvider = outputProvider;
			this.writeParameters = writeParameters;
		}
		
		public void setIntermediateParameters(Object[] parameters) {
			this.intermediateParameters = parameters;
		}
		
		public AsyncWork<Void,Exception> execute(byte priority) {
			OperationExecution first = OperationExecution.from(read, readParameters, priority);
			OperationExecution last = first;
			for (int i = 0; i < intermediates.length; ++i) {
				OperationExecution op = OperationExecution.from(intermediates[i], intermediateParameters[i], priority);
				last.setNext(op);
				last = op;
			}
			OperationExecution end = new WriteOneToOne((DataFormatWriteOperation.OneToOne)write, writeParameters, priority, nameGenerator, outputProvider);
			last.setNext(end);
			for (Data in : input) {
				OperationExecutionInput inp = new OperationExecutionInput();
				inp.input = in;
				inp.originalName = in.getName();
				inp.variables = new ArrayList<>();
				first.addInput(inp);
			}
			first.endOfInputs();
			AsyncWork<Void,Exception> sp = new AsyncWork<>();
			end.ondone.listenInline(new Runnable() {
				@Override
				public void run() {
					if (end.error != null)
						sp.unblockError(end.error);
					else
						sp.unblockSuccess(null);
				}
			});
			return sp;
		}
		
		private static class OperationExecutionInput {
			private Object input;
			private String originalName;
			private List<Pair<Localized,String>> variables;
		}
		
		public static abstract class OperationExecution {
			protected OperationExecution(Object parameters, byte priority) { this.parameters = parameters; this.priority = priority; }
			protected Object parameters;
			protected byte priority;
			protected OperationExecution next;
			protected ArrayList<OperationExecutionInput> inputs = new ArrayList<>();
			protected SynchronizationPoint<NoException> ondone = new SynchronizationPoint<NoException>();
			protected Exception error = null;
			protected WorkProgress progress = new WorkProgressImpl(0);
			public final void setNext(OperationExecution next) { this.next = next; }
			public void addInput(OperationExecutionInput input) { this.inputs.add(input); }
			public void endOfInputs() {}
			public WorkProgress getProgress() { return progress; }
			
			@SuppressWarnings("rawtypes")
			public static OperationExecution from(DataFormatReadOperation read, Object parameters, byte priority) {
				if (read instanceof DataFormatReadOperation.OneToOne)
					return new ReadOneToOne((DataFormatReadOperation.OneToOne)read, parameters, priority);
				if (read instanceof DataFormatReadOperation.OneToMany)
					return new ReadOneToMany((DataFormatReadOperation.OneToMany)read, parameters, priority);
				if (read instanceof DataFormatReadOperation.ManyToOne)
					return new ReadManyToOne((DataFormatReadOperation.ManyToOne)read, parameters, priority);
				return new ReadManyToMany((DataFormatReadOperation.ManyToMany)read, parameters, priority);
			}
			@SuppressWarnings("rawtypes")
			public static OperationExecution from(Operation op, Object parameters, byte priority) {
				if (op instanceof Operation.OneToOne)
					return new OperationOneToOne((Operation.OneToOne)op, parameters, priority);
				if (op instanceof Operation.OneToMany)
					return new OperationOneToMany((Operation.OneToMany)op, parameters, priority);
				if (op instanceof Operation.ManyToOne)
					return new OperationManyToOne((Operation.ManyToOne)op, parameters, priority);
				return new OperationManyToMany((Operation.ManyToMany)op, parameters, priority);
			}
		}
		@SuppressWarnings("rawtypes")
		public static abstract class OneToOneExecution extends OperationExecution {
			public OneToOneExecution(Object parameters, byte priority) {
				super(parameters, priority);
			}
			protected ArrayList<AsyncWork> processes = new ArrayList<>();
			protected JoinPoint jp = new JoinPoint();
			protected abstract AsyncWork execute(OperationExecutionInput input, WorkProgress progress, long work);
			protected abstract void release(Object input, Object output);
			@SuppressWarnings("unchecked")
			@Override
			public void addInput(OperationExecutionInput input) {
				if (error != null) return;
				super.addInput(input);
				progress.setAmount(progress.getAmount() + 1000);
				AsyncWork sp = execute(input, progress, 1000);
				processes.add(sp);
				jp.addToJoin(1);
				sp.listenInline(new AsyncWorkListener<Object,Exception>() {
					@Override
					public void ready(Object result) {
						if (error == null) {
							OperationExecutionInput nextInput = new OperationExecutionInput();
							nextInput.input = result;
							nextInput.originalName = input.originalName;
							nextInput.variables = input.variables;
							next.addInput(nextInput);
						}
						jp.joined();
					}
					@Override
					public void error(Exception error) {
						OneToOneExecution.this.error = error;
						jp.joined();
					}
					@Override
					public void cancelled(CancelException event) {
						error = event;
						jp.joined();
					}
				});
			}
			@Override
			public void endOfInputs() {
				jp.start();
				jp.listenInline(new Runnable() {
					@Override
					public void run() {
						if (error != null) next.error = error;
						next.endOfInputs();
						ondone.unblock();
						progress.done();
					}
				});
				// when next operation is done, we can call the release method
				next.ondone.listenAsynch(new Task.Cpu<Void,NoException>("Release", priority){
					@Override
					public Void run() {
						for (int i = 0; i < processes.size(); ++i)
							release(inputs.get(i).input, processes.get(i).getResult());
						return null;
					}
				}, true);
			}
		}
		@SuppressWarnings("rawtypes")
		public static abstract class OneToManyExecution extends OperationExecution {
			public OneToManyExecution(Object parameters, byte priority) {
				super(parameters, priority);
			}
			protected ArrayList<AsyncWork> processes = new ArrayList<>();
			protected JoinPoint jp = new JoinPoint();
			protected abstract AsyncWork execute(OperationExecutionInput input, WorkProgress progress, long work);
			protected abstract void release(Object input, List<Object> output);
			protected abstract Localized getVariableName();
			@SuppressWarnings("unchecked")
			@Override
			public void addInput(OperationExecutionInput input) {
				if (error != null) return;
				super.addInput(input);
				progress.setAmount(progress.getAmount() + 1000);
				AsyncWork sp = execute(input, progress, 1000);
				processes.add(sp);
				jp.addToJoin(1);
				sp.listenInline(new AsyncWorkListener<List<Object>,Exception>() {
					@Override
					public void ready(List<Object> result) {
						if (error == null) {
							for (int i = 0; i < result.size(); ++i) {
								OperationExecutionInput nextInput = new OperationExecutionInput();
								nextInput.input = result.get(i);
								nextInput.originalName = input.originalName;
								nextInput.variables = new ArrayList<>();
								nextInput.variables.add(new Pair<>(getVariableName(), Integer.toString(i+1)));
								next.addInput(nextInput);
							}
						}
						jp.joined();
					}
					@Override
					public void error(Exception error) {
						OneToManyExecution.this.error = error;
						jp.joined();
					}
					@Override
					public void cancelled(CancelException event) {
						error = event;
						jp.joined();
					}
				});
			}
			@Override
			public void endOfInputs() {
				jp.start();
				jp.listenInline(new Runnable() {
					@Override
					public void run() {
						if (error != null) next.error = error;
						next.endOfInputs();
						ondone.unblock();
						progress.done();
					}
				});
				// when next operation is done, we can call the release method
				next.ondone.listenAsynch(new Task.Cpu<Void,NoException>("Release", priority){
					@SuppressWarnings("unchecked")
					@Override
					public Void run() {
						for (int i = 0; i < processes.size(); ++i)
							release(inputs.get(i).input, (List)processes.get(i).getResult());
						return null;
					}
				}, true);
			}
		}
		@SuppressWarnings("rawtypes")
		public static abstract class ManyToOneExecution extends OperationExecution {
			public ManyToOneExecution(Object parameters, byte priority) {
				super(parameters, priority);
			}
			protected abstract AsyncWork execute(List<OperationExecutionInput> input, WorkProgress progress, long work);
			protected abstract void release(List<Object> input, Object output);
			@Override
			public void endOfInputs() {
				progress.setAmount(1000 * inputs.size());
				AsyncWork sp = execute(inputs, progress, progress.getAmount());
				sp.listenInline(new Runnable() {
					@Override
					public void run() {
						if (!sp.isSuccessful()) {
							if (sp.isCancelled()) error = sp.getCancelEvent();
							else error = sp.getError();
						} else {
							OperationExecutionInput nextInput = new OperationExecutionInput();
							nextInput.input = sp.getResult();
							nextInput.originalName = "";
							nextInput.variables = new ArrayList<>(0);
							next.addInput(nextInput);
						}
						if (error != null) next.error = error;
						next.endOfInputs();
						ondone.unblock();
						progress.done();
					}
				});
				// when next operation is done, we can call the release method
				next.ondone.listenAsynch(new Task.Cpu<Void,NoException>("Release", priority){
					@Override
					public Void run() {
						ArrayList<Object> list = new ArrayList<>(inputs.size());
						for (OperationExecutionInput input : inputs) list.add(input.input);
						release(list, sp.getResult());
						return null;
					}
				}, true);
			}
		}
		@SuppressWarnings("rawtypes")
		public static abstract class ManyToManyExecution extends OperationExecution {
			public ManyToManyExecution(Object parameters, byte priority) {
				super(parameters, priority);
			}
			protected abstract AsyncWork execute(List<OperationExecutionInput> input, WorkProgress progress, long work);
			protected abstract void release(List<Object> input, List<Object> output);
			protected abstract Localized getVariableName();
			@Override
			public void endOfInputs() {
				progress.setAmount(1000 * inputs.size());
				AsyncWork sp = execute(inputs, progress, progress.getAmount());
				sp.listenInline(new Runnable() {
					@Override
					public void run() {
						if (!sp.isSuccessful()) {
							if (sp.isCancelled()) error = sp.getCancelEvent();
							else error = sp.getError();
						} else {
							List res = (List)sp.getResult();
							for (int i = 0; i < res.size(); ++i) {
								OperationExecutionInput nextInput = new OperationExecutionInput();
								nextInput.input = res.get(i);
								nextInput.originalName = "";
								nextInput.variables = new ArrayList<>();
								nextInput.variables.add(new Pair<>(getVariableName(), Integer.toString(i+1)));
								next.addInput(nextInput);
							}
						}
						if (error != null) next.error = error;
						next.endOfInputs();
						ondone.unblock();
						progress.done();
					}
				});
				// when next operation is done, we can call the release method
				next.ondone.listenAsynch(new Task.Cpu<Void,NoException>("Release", priority){
					@SuppressWarnings("unchecked")
					@Override
					public Void run() {
						ArrayList<Object> list = new ArrayList<>(inputs.size());
						for (OperationExecutionInput input : inputs) list.add(input.input);
						release(list, (List)sp.getResult());
						return null;
					}
				}, true);
			}
		}

		@SuppressWarnings("rawtypes")
		public static class ReadOneToOne extends OneToOneExecution {
			public ReadOneToOne(DataFormatReadOperation.OneToOne op, Object parameters, byte priority) {
				super(parameters, priority);
				this.op = op;
			}
			private DataFormatReadOperation.OneToOne op;
			@SuppressWarnings("unchecked")
			@Override
			protected AsyncWork execute(OperationExecutionInput input, WorkProgress progress, long work) {
				return op.execute((Data)input.input, parameters, priority, progress, work);
			}
			@SuppressWarnings("unchecked")
			@Override
			protected void release(Object input, Object output) {
				op.release((Data)input, output);
			}
		}
		@SuppressWarnings("rawtypes")
		public static class ReadOneToMany extends OneToManyExecution {
			public ReadOneToMany(DataFormatReadOperation.OneToMany op, Object parameters, byte priority) {
				super(parameters, priority);
				this.op = op;
			}
			private DataFormatReadOperation.OneToMany op;
			@SuppressWarnings("unchecked")
			@Override
			protected AsyncWork execute(OperationExecutionInput input, WorkProgress progress, long work) {
				return op.execute((Data)input.input, parameters, priority, progress, work);
			}
			@SuppressWarnings("unchecked")
			@Override
			protected void release(Object input, List<Object> output) {
				op.release((Data)input, output);
			}
			@Override
			protected Localized getVariableName() {
				return op.getVariableName();
			}
		}
		@SuppressWarnings("rawtypes")
		public static class ReadManyToOne extends ManyToOneExecution {
			public ReadManyToOne(DataFormatReadOperation.ManyToOne op, Object parameters, byte priority) {
				super(parameters, priority);
				this.op = op;
			}
			private DataFormatReadOperation.ManyToOne op;
			@SuppressWarnings("unchecked")
			@Override
			protected AsyncWork execute(List<OperationExecutionInput> input, WorkProgress progress, long work) {
				ArrayList<Object> list = new ArrayList<>(inputs.size());
				for (OperationExecutionInput in : input) list.add(in.input);
				return op.execute(list, parameters, priority, progress, work);
			}
			@SuppressWarnings("unchecked")
			@Override
			protected void release(List<Object> input, Object output) {
				op.release(input, output);
			}
		}
		@SuppressWarnings("rawtypes")
		public static class ReadManyToMany extends ManyToManyExecution {
			public ReadManyToMany(DataFormatReadOperation.ManyToMany op, Object parameters, byte priority) {
				super(parameters, priority);
				this.op = op;
			}
			private DataFormatReadOperation.ManyToMany op;
			@SuppressWarnings("unchecked")
			@Override
			protected AsyncWork execute(List<OperationExecutionInput> input, WorkProgress progress, long work) {
				ArrayList<Object> list = new ArrayList<>(inputs.size());
				for (OperationExecutionInput in : input) list.add(in.input);
				return op.execute(list, parameters, priority, progress, work);
			}
			@SuppressWarnings("unchecked")
			@Override
			protected void release(List<Object> input, List<Object> output) {
				op.release(input, output);
			}
			@Override
			protected Localized getVariableName() {
				return op.getVariableName();
			}
		}
		
		@SuppressWarnings("rawtypes")
		public static class OperationOneToOne extends OneToOneExecution {
			public OperationOneToOne(Operation.OneToOne op, Object parameters, byte priority) {
				super(parameters, priority);
				this.op = op;
			}
			private Operation.OneToOne op;
			@SuppressWarnings("unchecked")
			@Override
			protected AsyncWork execute(OperationExecutionInput input, WorkProgress progress, long work) {
				return op.execute(input.input, parameters, priority, progress, work);
			}
			@SuppressWarnings("unchecked")
			@Override
			protected void release(Object input, Object output) {
				op.release(output);
			}
		}
		@SuppressWarnings("rawtypes")
		public static class OperationOneToMany extends OneToManyExecution {
			public OperationOneToMany(Operation.OneToMany op, Object parameters, byte priority) {
				super(parameters, priority);
				this.op = op;
			}
			private Operation.OneToMany op;
			@SuppressWarnings("unchecked")
			@Override
			protected AsyncWork execute(OperationExecutionInput input, WorkProgress progress, long work) {
				return op.execute(input.input, parameters, priority, progress, work);
			}
			@SuppressWarnings("unchecked")
			@Override
			protected void release(Object input, List<Object> output) {
				op.release(output);
			}
			@Override
			protected Localized getVariableName() {
				return op.getVariableName();
			}
		}
		@SuppressWarnings("rawtypes")
		public static class OperationManyToOne extends ManyToOneExecution {
			public OperationManyToOne(Operation.ManyToOne op, Object parameters, byte priority) {
				super(parameters, priority);
				this.op = op;
			}
			private Operation.ManyToOne op;
			@SuppressWarnings("unchecked")
			@Override
			protected AsyncWork execute(List<OperationExecutionInput> input, WorkProgress progress, long work) {
				ArrayList<Object> list = new ArrayList<>(inputs.size());
				for (OperationExecutionInput in : input) list.add(in.input);
				return op.execute(list, parameters, priority, progress, work);
			}
			@SuppressWarnings("unchecked")
			@Override
			protected void release(List<Object> input, Object output) {
				op.release(output);
			}
		}
		@SuppressWarnings("rawtypes")
		public static class OperationManyToMany extends ManyToManyExecution {
			public OperationManyToMany(Operation.ManyToMany op, Object parameters, byte priority) {
				super(parameters, priority);
				this.op = op;
			}
			private Operation.ManyToMany op;
			@SuppressWarnings("unchecked")
			@Override
			protected AsyncWork execute(List<OperationExecutionInput> input, WorkProgress progress, long work) {
				ArrayList<Object> list = new ArrayList<>(inputs.size());
				for (OperationExecutionInput in : input) list.add(in.input);
				return op.execute(list, parameters, priority, progress, work);
			}
			@SuppressWarnings("unchecked")
			@Override
			protected void release(List<Object> input, List<Object> output) {
				op.release(output);
			}
			@Override
			protected Localized getVariableName() {
				return op.getVariableName();
			}
		}
		
		@SuppressWarnings("rawtypes")
		public static class WriteOneToOne extends OneToOneExecution {
			public WriteOneToOne(DataFormatWriteOperation.OneToOne op, Object parameters, byte priority, OutputNameGenerator nameGenerator, Provider.FromValue<String, AsyncWork<IO.Writable,IOException>> outputProvider) {
				super(parameters, priority);
				this.op = op;
				this.nameGenerator = nameGenerator;
				this.outputProvider = outputProvider;
			}
			private DataFormatWriteOperation.OneToOne op;
			private OutputNameGenerator nameGenerator;
			private Provider.FromValue<String, AsyncWork<IO.Writable,IOException>> outputProvider;
			@SuppressWarnings("unchecked")
			@Override
			protected AsyncWork execute(OperationExecutionInput input, WorkProgress progress, long work) {
				String name = nameGenerator.getOutputName(input.originalName, input.variables);
				AsyncWork<IO.Writable,IOException> output = outputProvider.provide(name);
				AsyncWork<Void,Exception> sp = new AsyncWork<>();
				output.listenInline(new Runnable() {
					@Override
					public void run() {
						if (!output.isSuccessful()) {
							error = output.getError();
							sp.unblockError(error);
							return;
						}
						AsyncWork write = op.execute(input.input, output.getResult(), parameters, priority, progress, work);
						write.listenInline(new Runnable() {
							@Override
							public void run() {
								output.getResult().closeAsync();
							}
						});
						write.listenInline(sp);
					}
				});
				return sp;
			}
			@SuppressWarnings("unchecked")
			@Override
			protected void release(Object input, Object output) {
			}
		}
	}

	
	public static interface DataFormatReadOperationWithIntermediateFormat {
		
		public static class OneToOne<Input extends DataFormat, Output> implements DataFormatReadOperation.OneToOne<Input,Output,CompositeObject> {
			
			public OneToOne(Input input, Class<Output> output, DataFormatReadOperation.OneToOne<Input,?,?> read1, DataFormatWriteOperation.OneToOne<?, ? extends DataFormat, ?> write, DataFormatReadOperation.OneToOne<? extends DataFormat,Output,?> read2) {
				this.input = input;
				this.output = output;
				this.read1 = (DataFormatReadOperation.OneToOne<Input,?,Object>)read1;
				this.write = (DataFormatWriteOperation.OneToOne<Object, ? extends DataFormat, Object>)write;
				this.read2 = (DataFormatReadOperation.OneToOne<? extends DataFormat,Output,Object>)read2;
			}
			
			private Input input;
			private Class<Output> output;
			private DataFormatReadOperation.OneToOne<Input,?,Object> read1;
			private DataFormatWriteOperation.OneToOne<Object, ? extends DataFormat, Object> write;
			private DataFormatReadOperation.OneToOne<? extends DataFormat,Output,Object> read2;
			
			@Override
			public AsyncWork<Output, Exception> execute(Data data, CompositeObject params, byte priority, WorkProgress progress, long work) {
				AsyncWork<Output, Exception> sp = new AsyncWork<Output, Exception>();
				
				AsyncWork<?,? extends Exception> execRead1 = read1.execute(data, params.get(0), priority, progress, work/3);
				execRead1.listenInline(new Runnable() {
					@Override
					public void run() {
						if (execRead1.isCancelled()) {
							sp.unblockCancel(execRead1.getCancelEvent());
							return;
						}
						if (!execRead1.isSuccessful()) {
							sp.unblockError(execRead1.getError());
							return;
						}
						Object intermediateObject = execRead1.getResult();
						IOInMemoryOrFile io = new IOInMemoryOrFile(1024*1024, priority, "temporary conversion");
						AsyncWork<Void,? extends Exception> execWrite = write.execute(intermediateObject, io, params.get(1), priority, progress, work/3);
						execWrite.listenInline(new Runnable() {
							@Override
							public void run() {
								if (execWrite.isCancelled()) {
									sp.unblockCancel(execWrite.getCancelEvent());
									try { io.close(); } catch (IOException e) {}
									return;
								}
								if (!execWrite.isSuccessful()) {
									sp.unblockError(execWrite.getError());
									try { io.close(); } catch (IOException e) {}
									return;
								}
								io.seekSync(SeekType.FROM_BEGINNING, 0);
								AsyncWork<Output,? extends Exception> execRead2;
								try { execRead2 = read2.execute(new TempData("Temporary data", io), params.get(2), priority, progress, work-2*(work/3)); }
								catch (IOException e) {
									try { io.close(); } catch (IOException e2) {}
									sp.unblockError(e);
									return;
								}
								execRead2.listenInline(new Runnable() {
									@Override
									public void run() {
										try { io.close(); } catch (IOException e) {}
										if (execRead2.isCancelled()) {
											sp.unblockCancel(execRead2.getCancelEvent());
											return;
										}
										if (!execRead2.isSuccessful()) {
											sp.unblockError(execRead2.getError());
											return;
										}
										sp.unblockSuccess(execRead2.getResult());
									}
								});
							}
						});
					}
				});
				// TODO listen to cancel
				
				return sp;
			}

			@Override
			public Input getInputFormat() { return input; }
			@Override
			public Class<Output> getOutputType() { return output; }

			@Override
			public Localized getOutputName() {
				return new FixedLocalizedString("Intermediate format");
			}

			@Override
			public Localized getName() {
				return new FixedLocalizedString("Intermediate format");
			}

			@Override
			public Class<CompositeObject> getParametersClass() {
				return CompositeObject.class;
			}

			@Override
			public CompositeObject createDefaultParameters() {
				CompositeObject o = new CompositeObject();
				o.add(read1.getName(), read1.createDefaultParameters());
				o.add(write.getName(), write.createDefaultParameters());
				o.add(read2.getName(), read2.createDefaultParameters());
				return o;
			}

			@Override
			public void release(Data data, Output output) {
				// TODO Auto-generated method stub
				
			}
		}
		
	}
	*/
}
