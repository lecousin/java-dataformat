package net.lecousin.dataformat.archive.zip;

class ZipCompressor {
	/*
	static class Listener {
		LinkedList<ZipCompressor> readyToBeWritten = new LinkedList<>();
		LinkedList<ZipCompressor> maxOutputsReached = new LinkedList<>();
		LinkedList<ZipCompressor> inProgress = new LinkedList<>();
		LinkedList<ZipCompressor> inError = new LinkedList<>();
		SynchronizationPoint sp = new SynchronizationPoint();
	}
	
	public ZipCompressor(FileToZip file, byte priority, int maxOutputs, Buffers buffers, Listener listener) {
		this.priority = priority;
		this.file = file;
		this.deflater = new Deflater(file.compressionLevel);
		this.buffers = buffers;
		this.outputs = new ArrayList<>(maxOutputs);
		this.maxOutputs = maxOutputs;
		this.listener = listener;
	}

	FileToZip file;
	private byte priority;
	private Deflater deflater;
	private Buffers buffers;
	ArrayList<ByteBuffer> outputs;
	private int maxOutputs;
	private Listener listener;
	long compressedSize = 0;
	long uncompressedSize = 0;
	CRC32 crc32 = new CRC32();
	
	Task<Void,NoException> compression = null;
	boolean endReached = false;
	IOException error = null;
	
	public void start() {
		synchronized (listener) { listener.inProgress.add(this); }
		read();
	}
	
	private void read() {
		ByteBuffer input = buffers.getBuffer();
		AsyncWork<Integer,IOException> read = file.stream.readFullyAsync(input);
		read.listenInline(new Runnable() {
			@Override
			public void run() {
				if (!read.isSuccessful()) {
					error = read.getError();
					SynchronizationPoint sp;
					synchronized (listener) {
						listener.inError.add(ZipCompressor.this);
						listener.inProgress.remove(ZipCompressor.this);
						sp = listener.sp;
						listener.sp = new SynchronizationPoint();
					}
					sp.unblock();
					return;
				}
				int nb = read.getResult().intValue();
				if (nb < input.capacity())
					endReached = true;
				uncompressedSize += nb;
				if (compression == null || compression.isDone()) {
					compression = compress(input);
					if (endReached) {
						compression.getSynchOnDone().listenInline(new Runnable() {
							@Override
							public void run() {
								SynchronizationPoint sp;
								synchronized (listener) {
									listener.readyToBeWritten.add(ZipCompressor.this);
									listener.inProgress.remove(ZipCompressor.this);
									sp = listener.sp;
									listener.sp = new SynchronizationPoint();
								}
								sp.unblock();
							}
						});
					} else if (outputs.size() >= maxOutputs) {
						SynchronizationPoint sp;
						synchronized (listener) {
							listener.maxOutputsReached.add(ZipCompressor.this);
							listener.inProgress.remove(ZipCompressor.this);
							sp = listener.sp;
							listener.sp = new SynchronizationPoint();
						}
						sp.unblock();
					} else
						read();
				} else
					compression.getSynchOnDone().listenInline(new Runnable() {
						@Override
						public void run() {
							compression = compress(input);
							if (endReached) {
								compression.getSynchOnDone().listenInline(new Runnable() {
									@Override
									public void run() {
										SynchronizationPoint sp;
										synchronized (listener) {
											listener.readyToBeWritten.add(ZipCompressor.this);
											listener.inProgress.remove(ZipCompressor.this);
											sp = listener.sp;
											listener.sp = new SynchronizationPoint();
										}
										sp.unblock();
									}
								});
							} else if (outputs.size() >= maxOutputs) {
								SynchronizationPoint sp;
								synchronized (listener) {
									listener.maxOutputsReached.add(ZipCompressor.this);
									listener.inProgress.remove(ZipCompressor.this);
									sp = listener.sp;
									listener.sp = new SynchronizationPoint();
								}
								sp.unblock();
							} else
								read();
						}
					});
			}
		});
	}
	
	private Task<Void,NoException> compress(ByteBuffer input) {
		Task<Void,NoException> task = new Task.Cpu<Void,NoException>("Zip compression", priority) {
			@Override
			public Void run() {
				if (input.position() > 0)
					deflater.setInput(input.array(), 0, input.position());
				if (endReached)
					deflater.finish();
				boolean bufferDone = false;
				while (!deflater.needsInput()) {
					// check if last buffer is not yet full
					if (!outputs.isEmpty()) {
						ByteBuffer buf = outputs.get(outputs.size()-1);
						byte[] b = buf.array();
						if (buf.limit() < b.length) {
							int nb = deflater.deflate(b, buf.limit(), b.length-buf.limit());
							if (nb > 0) {
								compressedSize += nb;
								crc32.update(b, buf.limit(), nb);
								buf.limit(buf.limit()+nb);
								if (buf.limit() == buf.capacity())
									bufferDone = true;
							}
							continue;
						}
					}
					// need a new output buffer
					ByteBuffer buf = buffers.getBuffer();
					int nb = deflater.deflate(buf.array(), 0, buf.array().length);
					compressedSize += nb;
					crc32.update(buf.array(), 0, nb);
					buf.limit(nb);
					synchronized (outputs) {
						outputs.add(buf);
					}
				}
				if (endReached)
					deflater.end();
				buffers.freeBuffer(input);
				if (bufferDone) {
					SynchronizationPoint sp;
					synchronized (listener) {
						sp = listener.sp;
						listener.sp = new SynchronizationPoint();
					}
					sp.unblock();
				}
				return null;
			}
		};
		task.startDetached();
		return task;
	}
	*/
}
