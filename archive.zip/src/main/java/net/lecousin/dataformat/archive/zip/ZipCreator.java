package net.lecousin.dataformat.archive.zip;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.collections.LinkedArrayList;
import net.lecousin.framework.collections.TurnArray;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.TaskManager;
import net.lecousin.framework.concurrent.Threading;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.IO.Seekable.SeekType;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.Provider;

public class ZipCreator {
	
	private static Logger logger = LCCore.getApplication().getLoggerFactory().getLogger(ZipCreator.class);
	
	// TODO priority on disk:
	// - 1: write to zip from memory
	// TODO write to zip from memory in sequence (single task)
	// TODO instead of n buffers of same size, a single big buffer that we can use using ranges
	
	/*
	 * Multi-processors compression:
	 *  - small files compressed in memory, then written to file
	 *  - big files compressed to a temporary file, then copy content to file
	 * 
	 * - maximum memory usage: list of buffers that we reuse once they are written
	 *   - buffers to read from file, and give to CPU to compress
	 *   - buffers where we store compressed content, and give to write
	 *   => we need to keep enough buffers to compress, not all to read...
	 * 
	 * - to try to reduce disk move, we should read enough large buffers at once
	 * 
	 */
	
	public ZipCreator(IO.Writable.Seekable output, int maxMemory, File outputFile) {
		this.output = output;
		this.outputFile = outputFile;
		initMemory(maxMemory);
	}
	
	public ZipCreator(File file, byte priority, int maxMemory) throws IOException {
		if (file.exists()) throw new IOException("File already exists: " + file.getAbsolutePath());
		if (!file.createNewFile()) throw new IOException("Unable to create file " + file.getAbsolutePath());
		this.outputFile = file;
		this.output = new FileIO.WriteOnly(file, priority);
		initMemory(maxMemory);
	}
	
	public SynchronizationPoint<Exception> getSynch() {
		return end;
	}
	public List<Pair<String,Exception>> getFilesInError() {
		ArrayList<Pair<String,Exception>> list = new ArrayList<>(errors.size());
		for (ToZip f : errors)
			list.add(new Pair<>(f.pathInZip, f.error));
		return list;
	}
	
	/* output */
	private IO.Writable.Seekable output;
	private File outputFile;
	private int tmpFileCounter = 0;
	private ArrayList<ToZip> errors = new ArrayList<>(2);
	private Exception fatalError = null;
	private SynchronizationPoint<Exception> end = new SynchronizationPoint<>();
	
	private void fatalError(Exception e) {
		if (logger.error()) logger.error("Fatal error", e);
		if (fatalError == null) // keep only the first one, because subsequent may be caused by the first one
			fatalError = e;
		end();
	}
	private void error(ToZip file, Exception error) {
		if (logger.error()) logger.error("Error zipping file " + file.pathInZip, error);
		if (file.error != null) return;
		file.error = error;
		errors.add(file);
	}
	
	/* Options */
	// TODO keep date, store user...
	
	/* Buffers */
	private int nbCPUs;
	private int bufferSize, maxBuffers;
	private TurnArray<byte[]> availableBuffers;
	private int buffersInUse = 0;
	
	private byte[] getBuffer(boolean toRead) {
		synchronized (availableBuffers) {
			if (toRead && availableBuffers.size() + maxBuffers - buffersInUse < 2)
				return null;
			byte[] buffer = availableBuffers.pollFirst();
			if (buffer == null) {
				if (buffersInUse == maxBuffers)
					return null;
				buffer = new byte[bufferSize];
			}
			buffersInUse++;
			return buffer;
		}
	}
	
	private void freeBuffer(byte[] buffer) {
		ReadFile startReading = null;
		synchronized (toZip) {
			synchronized (availableBuffers) {
				CompressFile compressor = compressorsWaiting.pollFirst();
				if (compressor != null) {
					compressor.outputBuffer = buffer;
					compressor.compressing.start();
					return;
				}
				for (int i = reading.length-1; i >= 0; --i)
					if (reading[i] != null && reading[i].buffer == null) {
						reading[i].buffer = buffer;
						startReading = reading[i];
						break;
					}
				if (startReading == null) {
					availableBuffers.addLast(buffer);
					buffersInUse--;
				}
			}
		}
		if (startReading != null)
			startReading.start();
	}
	
	private void initMemory(int maxMemory) {
		nbCPUs = Runtime.getRuntime().availableProcessors();
		reading = new ReadFile[nbCPUs+1];
		compressorsWaiting = new TurnArray<>(nbCPUs+1);
		readyToWrite = new TurnArray<>(nbCPUs*2);
		// when a buffer is compressed, it uses 2 buffers: input and output
		// and when a buffer is compressed we want to be able to at least fill another one
		// so it can be compressed immediately when the first one is done.
		// it means at least nbCPU * 3 buffers
		// we want files up to 4M to be compressed in memory
		// it means we need at least nbCPU * 3 * 4M of memory
		int minMemory = nbCPUs * 3 * 4*1024*1024;
		if (maxMemory < minMemory)
			maxMemory = minMemory;
		
		// we want buffers to be at least 64K, and up to 1M if possible
		if (maxMemory < 16*1024*1024)
			bufferSize = 64*1024;
		else if (maxMemory < 32*1024*1024)
			bufferSize = 128*1024;
		else if (maxMemory < 64*1024*1024)
			bufferSize = 256*1024;
		else if (maxMemory < 128*1024*1024)
			bufferSize = 512*1024;
		else
			bufferSize = 1024*1024;
		maxBuffers = maxMemory/bufferSize;
		// let's increase buffer size if more than 1024 buffers
		while (maxBuffers > 1024) {
			bufferSize = bufferSize * 3 / 2;
			maxBuffers = maxMemory/bufferSize;
		}
		// some examples:
		// 1 CPU: minMemory = 12M => 192 buffers of 64K
		// 2 CPU: minMemory = 24M => 192 buffers of 128K
		// 4 CPU: minMemory = 48M => 192 buffers of 256K
		// 8 CPU: minMemory = 96M => 192 buffers of 512K
		// 256M => 256 buffers of 1M
		// 512M => 512 buffers of 1M
		// 1G => 1024 buffers of 1M
		// 2G => 1024 buffers of 2M
		
		availableBuffers = new TurnArray<>(maxBuffers);
	}
	
	/* List of files to zip */
	
	private static class ToZip {
		public Provider<IO.Readable> opener;
		public TaskManager manager;
		public IO.Readable input;
		public long inputSize; // TODO get it from caller if possible
		public CompressFile compressor;
		public String pathInZip;
		public Exception error;
		public long compressedSize;
		public long crc32;
		public long localHeaderOffset;
		public boolean done;
	}
	private HashMap<TaskManager,TurnArray<ToZip>> toZip = new HashMap<>(4);
	private LinkedArrayList<ToZip> allFiles = new LinkedArrayList<>(100);
	private boolean noMoreFile = false;
	
	public void add(File file, String pathInZip) {
		ToZip t = new ToZip();
		t.opener = new Provider<IO.Readable>() {
			@Override
			public Readable provide() {
				return new FileIO.ReadOnly(file, output.getPriority());
			}
		};
		t.manager = Threading.getDrivesTaskManager().getTaskManager(file);
		t.pathInZip = pathInZip;
		add(t);
	}
	
	public <T extends IO.Readable&IO.KnownSize> void add(T file, String pathInZip) {
		// TODO better about task manager ?
		add(file, file.getTaskManager(), pathInZip);
	}
	public <T extends IO.Readable&IO.KnownSize> void add(T file, TaskManager manager, String pathInZip) {
		// TODO better about task manager ?
		ToZip t = new ToZip();
		t.input = file;
		t.manager = manager;
		t.pathInZip = pathInZip;
		add(t);
	}
	
	public void noMoreFile() {
		noMoreFile = true;
		if (logger.debug()) logger.debug("No more file to zip");
		checkEnd();
	}
	
	private void checkEnd() {
		synchronized (allFiles) {
			if (!noMoreFile) return;
			if (writing != null) return;
			synchronized (toZip) { if (!toZip.isEmpty()) return; }
			for (ToZip file : allFiles)
				if (!file.done && file.error == null)
					return;
		}
		if (logger.debug()) logger.debug("No more data to write in zip, write central directory and close");
		writeCentralDirectoryAndClose();
	}
	
	// TODO start to write a compressed file as soon as possible, even we don't know yet its size. Preferably a small file...
	
	// TODO we use temporary file in the same drive as output, try to change this
	
	/* Reading files */
	
	private ReadFile[] reading;
	
	private void add(ToZip t) {
		if (logger.debug()) logger.debug("New file to zip: " + t.pathInZip);
		if (fatalError != null) return;
		synchronized (allFiles) { allFiles.add(t); }
		ReadFile read = null;
		synchronized (toZip) {
			for (int i = reading.length-1; i >= 0; --i)
				if (reading[i] == null) {
					reading[i] = read = new ReadFile(t);
					break;
				}
			if (read == null) {
				TurnArray<ToZip> queue = toZip.get(t.manager);
				if (queue == null) {
					queue = new TurnArray<>(128);
					toZip.put(t.manager, queue);
				}
				queue.addLast(t);
				return;
			}
			// we created the reading task, we need also a buffer to read inside
			read.buffer = getBuffer(true);
			if (read.buffer == null) // no available buffer, we will get one later
				return;
		}
		read.start();
	}
	
	private class ReadFile {
		public ReadFile(ToZip file) {
			this.file = file;
		}
		private ToZip file;
		private byte[] buffer;
		public void start() {
			if (file.input == null) {
				file.input = file.opener.provide();
				AsyncWork<Long,IOException> size = ((IO.KnownSize)file.input).getSizeAsync();
				size.listenInline(new Runnable() {
					@Override
					public void run() {
						if (fatalError != null) {
							file.input.closeAsync();
							return;
						}
						if (!size.isSuccessful()) {
							freeBuffer(buffer);
							error(file, size.getError());
							file.input.closeAsync();
							nextFile();
							return;
						}
						file.inputSize = size.getResult().longValue();
						if (logger.debug()) logger.debug("File size = " + file.inputSize + " for " + file.pathInZip);
						start();
					}
				});
				return;
			}
			if (logger.debug()) logger.debug("Reading from " + file.pathInZip);
			AsyncWork<Integer,IOException> read = file.input.readFullyAsync(ByteBuffer.wrap(buffer));
			read.listenAsync(new Task.Cpu<Void,NoException>("Get read buffer", Task.PRIORITY_IMPORTANT) {
				@Override
				public Void run() {
					if (fatalError != null) {
						file.input.closeAsync();
						return null;
					}
					if (!read.isSuccessful()) {
						if (logger.debug()) logger.debug("Error reading from " + file.pathInZip, read.getError());
						freeBuffer(buffer);
						error(file, read.getError());
						try { file.input.close(); } catch (Throwable t) {}
						nextFile();
						return null;
					}
					if (file.error != null) {
						freeBuffer(buffer);
						try { file.input.close(); } catch (Throwable t) {}
						nextFile();
						return null;
					}
					int nb = read.getResult().intValue();
					if (logger.debug()) logger.debug(nb + " bytes read from " + file.pathInZip);
					if (file.compressor == null)
						file.compressor = new CompressFile(file);
					if (nb <= 0) {
						freeBuffer(buffer);
						file.compressor.newBufferToCompress(null, 0, true);
						try { file.input.close(); } catch (Throwable t) {}
						if (logger.debug()) logger.debug("End of read for " + file.pathInZip);
						nextFile();
						return null;
					}
					// data to compress
					file.compressor.newBufferToCompress(buffer, nb, nb < buffer.length);
					if (nb == buffer.length) {
						// still data to read
						buffer = getBuffer(true);
						if (buffer == null) { // no available buffer, we will get one later
							if (logger.debug()) logger.debug("Wait for available buffer to read from " + file.pathInZip);
							return null;
						}
						ReadFile.this.start();
					} else {
						try { file.input.close(); } catch (Throwable t) {}
						if (logger.debug()) logger.debug("End of read for " + file.pathInZip);
						nextFile();
					}
					return null;
				}
			}, true);
		}
		private void nextFile() {
			if (fatalError != null) return;
			if (logger.debug()) logger.debug("Take next file to read");
			synchronized (toZip) {
				int myIndex = -1;
				ArrayList<TaskManager> used = new ArrayList<>(reading.length);
				for (int i = reading.length-1; i >= 0; --i) {
					if (reading[i] != null) {
						if (reading[i] != this)
							used.add(reading[i].file.manager);
						else
							myIndex = i;
					}
				}
				ToZip newFile = null;
				TurnArray<ToZip> nextQueue = null;
				for (Map.Entry<TaskManager, TurnArray<ToZip>> e : toZip.entrySet()) {
					if (!used.contains(e.getKey())) {
						newFile = e.getValue().pollFirst();
						if (e.getValue().isEmpty())
							toZip.remove(e.getKey());
						break;
					} else if (nextQueue == null)
						nextQueue = e.getValue();
				}
				if (newFile == null) {
					if (nextQueue == null) {
						reading[myIndex] = null;
						if (logger.debug()) logger.debug("No more file available to read");
						return;
					}
					newFile = nextQueue.pollFirst();
					if (nextQueue.isEmpty())
						toZip.remove(newFile.manager);
				}
				file = newFile;
				buffer = getBuffer(true);
				if (buffer == null) {// no available buffer, we will get one later
					if (logger.debug()) logger.debug("No available buffer to start reading from " + file.pathInZip);
					return;
				}
			}
			start();
		}
	}
	
	/* Compressing files */
	private TurnArray<CompressFile> compressorsWaiting;
	
	private class CompressFile {
		public CompressFile(ToZip file) {
			this.file = file;
			deflater = new Deflater(Deflater.BEST_COMPRESSION, true); // TODO level configurable
			if (logger.debug()) logger.debug("Compressor initialized for " + file.pathInZip);
		}
		private ToZip file;
		private Deflater deflater;
		private TurnArray<Pair<byte[],Integer>> queue = new TurnArray<>(5);
		private Compressing compressing = null;
		private byte[] outputBuffer = null;
		private int outputBufferPos = 0;
		private boolean endOfInput = false;

		public void newBufferToCompress(byte[] input, int len, boolean end) {
			if (logger.debug()) {
				logger.debug("New data to compress: " + len + " bytes for " + file.pathInZip);
				if (end) logger.debug("There will be no more data to compress for " + file.pathInZip);
			}
			// TODO empty files
			synchronized (this) {
				if (end) endOfInput = true;
				if (input != null)
					queue.addLast(new Pair<>(input, Integer.valueOf(len)));
				if (compressing != null)
					return;
				compressing = new Compressing();
			}
			if (input != null && outputBuffer == null) {
				synchronized (availableBuffers) {
					outputBuffer = getBuffer(false);
					if (outputBuffer != null)
						compressing.start();
					else {
						if (logger.debug()) logger.debug("No available buffer to compress " + file.pathInZip);
						compressorsWaiting.addLast(this);
					}
				}
			} else
				compressing.start();
		}
		
		private class Compressing extends Task.Cpu<Void, NoException> {
			private Compressing() {
				super("Compressing file", output.getPriority());
			}
			private byte[] inputSet = null;
			@Override
			public Void run() {
				if (logger.debug()) logger.debug("Compressing " + file.pathInZip);
				do {
					if (fatalError != null) {
						deflater.end();
						deflater = null;
						deflater = null;
						queue = null;
						error(null);
						return null;
					}
					if (file.error != null) {
						if (inputSet != null) freeBuffer(inputSet);
						if (outputBuffer != null) freeBuffer(outputBuffer);
						deflater.end();
						deflater = null;
						deflater = null;
						queue = null;
						error(null);
						return null;
					}
					if (inputSet == null) {
						Pair<byte[],Integer> input;
						synchronized (CompressFile.this) {
							input = queue.pollFirst();
							if (input == null && !endOfInput) {
								compressing = null;
								return null;
							}
						}
						if (input == null) {
							finishCompression();
							return null;
						}
						inputSet = input.getValue1();
						int nb = input.getValue2().intValue();
						deflater.setInput(inputSet, 0, nb);
						if (nb > 0) crc.update(inputSet, 0, nb);
					}
					while (!deflater.needsInput()) {
						int nbOutput = deflater.deflate(outputBuffer, outputBufferPos, outputBuffer.length-outputBufferPos);
						if (logger.debug()) logger.debug(nbOutput+" bytes of compressed data for " + file.pathInZip);
						if (nbOutput <= 0) break;
						outputBufferPos += nbOutput;
						if (outputBufferPos == outputBuffer.length) {
							// output buffer is full
							compressedDataReady(outputBuffer, outputBufferPos, false);
							outputBufferPos = 0;
							synchronized (availableBuffers) {
								outputBuffer = getBuffer(false);
								if (outputBuffer == null) {
									compressorsWaiting.addLast(CompressFile.this);
									if (logger.debug()) logger.debug("No available buffer to continue compressing " + file.pathInZip);
									compressing = new Compressing();
									compressing.inputSet = inputSet;
									return null;
								}
							}
						}
					}
					freeBuffer(inputSet);
					inputSet = null;
				} while (true);
			}
			private void finishCompression() {
				if (logger.debug()) logger.debug("Finishing compression of " + file.pathInZip);
				deflater.finish();
				while (!deflater.finished()) {
					int nbOutput = deflater.deflate(outputBuffer, outputBufferPos, outputBuffer.length-outputBufferPos);
					if (logger.debug()) logger.debug(nbOutput+" bytes of final compressed data for " + file.pathInZip);
					if (nbOutput <= 0) break;
					outputBufferPos += nbOutput;
					if (outputBufferPos == outputBuffer.length) {
						// output buffer is full
						compressedDataReady(outputBuffer, outputBufferPos, false);
						outputBufferPos = 0;
						outputBuffer = null;
						if (deflater.finished())
							break;
						synchronized (availableBuffers) {
							outputBuffer = getBuffer(false);
							if (outputBuffer == null) {
								if (logger.debug()) logger.debug("No available buffer to finish compressing " + file.pathInZip);
								compressorsWaiting.addLast(CompressFile.this);
								compressing = new Compressing();
								compressing.inputSet = inputSet;
								return;
							}
						}
					}
				}
				// compression done
				compressedDataReady(outputBuffer, outputBufferPos, true);
				deflater.end();
				deflater = null;
				queue = null;
			}
		}

		private LinkedList<ByteBuffer> compressedBuffers = new LinkedList<>();
		private boolean endOfCompressedData;
		private CRC32 crc = new CRC32();
		@SuppressWarnings("unused")
		private void compressedDataReady(byte[] buffer, int nb, boolean end) {
			if (fatalError != null) {
				error(null);
				return;
			}
			if (logger.debug()) logger.debug("New compressed data ready: " + nb + " bytes for " + file.pathInZip);
			if (logger.debug()) logger.debug("writinToTmp = " + writingToTmp + "; tmpFile = " + tmpFile + " ; file error = " + file.error);
			// compress until we reached almost 4M of compressed buffer
			if (writingToTmp || tmpFile != null || file.error != null) {
				synchronized (compressedBuffers) {
					if (file.error != null) {
						if (buffer != null)
							freeBuffer(buffer);
						return;
					}
					if (end) {
						endOfCompressedData = true;
						if (nb > 0)
							compressedBuffers.add(ByteBuffer.wrap(buffer, 0, nb));
						else if (buffer != null)
							freeBuffer(buffer);
						if (!writingToTmp) {
							if (logger.debug()) logger.debug("End of large file: " + file.pathInZip);
							try { addFileToWrite(new WriteLargeFile(file, this)); }
							catch (IOException e) { error(e); }
						}
					} else {
						if (writingToTmp)
							compressedBuffers.add(ByteBuffer.wrap(buffer, 0, nb));
						else {
							writingToTmp = true;
							listener.writingBuffer = buffer;
							tmpIO.writeAsync(ByteBuffer.wrap(buffer, 0, nb)).listenInline(listener);
						}
					}
				}
				return;
			}
			if (end) {
				if (nb > 0)
					compressedBuffers.add(ByteBuffer.wrap(buffer, 0, nb));
				else if (buffer != null)
					freeBuffer(buffer);
				file.crc32 = crc.getValue();
				file.compressedSize = 0;
				for (ByteBuffer b : compressedBuffers) file.compressedSize += b.remaining();
				addFileToWrite(new WriteToZipFromMemory(file, compressedBuffers.iterator()));
				compressedBuffers = null;
				return;
			}
			compressedBuffers.add(ByteBuffer.wrap(buffer, 0, nb));
			if ((compressedBuffers.size() + 1) * bufferSize > (4*1024*1024))
				new CreateTempFile();
		}
		// temporary file
		private File tmpFile = null;
		private FileIO.ReadWrite tmpIO = null;
		private WriteTmpListener listener = null;
		private boolean writingToTmp = false;
		private class CreateTempFile extends Task.OnFile<Void, NoException> {
			public CreateTempFile() {
				super(outputFile, "Create temporary file for big file to be compressed", output.getPriority());
				writingToTmp = true;
				start();
			}
			@Override
			public Void run() {
				if (logger.debug()) logger.debug("Creating temporary file for " + file.pathInZip);
				if (file.error != null) return null;
				if (fatalError != null) return null;
				synchronized (outputFile) {
					tmpFile = new File(outputFile.getParentFile(), outputFile.getName() + ".tmp." + (++tmpFileCounter));
				}
				if (tmpFile.exists()) tmpFile.delete();
				try {
					tmpFile.createNewFile();
				} catch (IOException e) {
					error(e);
					return null;
				}
				if (!tmpFile.exists()) {
					error(new Exception("Temporary file already exists anc cannot be removed: " + tmpFile.getAbsolutePath()));
					return null;
				}
				tmpIO = new FileIO.ReadWrite(tmpFile, output.getPriority());
				ByteBuffer buffer;
				synchronized (compressedBuffers) {
					buffer = compressedBuffers.removeFirst();
				}
				tmpIO.writeAsync(buffer).listenInline(listener = new WriteTmpListener(buffer.array()));
				return null;
			}
		}
		private class WriteTmpListener implements AsyncWorkListener<Integer,IOException> {
			public WriteTmpListener(byte[] buffer) {
				writingBuffer = buffer;
			}
			private byte[] writingBuffer;
			@Override
			public void ready(Integer result) {
				if (fatalError != null) {
					closeAndCleanTmp();
					return;
				}
				if (logger.debug()) logger.debug(result.intValue() + " bytes written to temporary file for " + file.pathInZip);
				ByteBuffer buffer;
				synchronized (compressedBuffers) {
					freeBuffer(writingBuffer);
					writingBuffer = null;
					if (file.error != null) return;
					if (fatalError != null) {
						closeAndCleanTmp();
						return;
					}
					if (endOfCompressedData) {
						if (logger.debug()) logger.debug("End of large file: " + file.pathInZip);
						try { addFileToWrite(new WriteLargeFile(file, CompressFile.this)); }
						catch (IOException e) {
							error(e);
						}
						return;
					}
					if (compressedBuffers.isEmpty()) {
						writingToTmp = false;
						return;
					}
					buffer = compressedBuffers.removeFirst();
					writingBuffer = buffer.array();
				}
				tmpIO.writeAsync(buffer).listenInline(this);
			}
			@Override
			public void error(IOException error) {
				freeBuffer(writingBuffer);
				CompressFile.this.error(error);
			}
			@Override
			public void cancelled(CancelException event) {
			}
		}
		private void error(Exception e) {
			if (e != null)
				ZipCreator.this.error(file, e);
			if (fatalError == null)
				synchronized (compressedBuffers) {
					while (!compressedBuffers.isEmpty())
						freeBuffer(compressedBuffers.removeFirst().array());
				}
			closeAndCleanTmp();
		}
		public void closeAndCleanTmp() {
			if (logger.debug()) logger.debug("Close and clean temporary file for " + file.pathInZip);
			if (tmpIO != null)
				try { tmpIO.close(); tmpIO = null; } catch (Throwable t) {}
			if (tmpFile != null)
				try { tmpFile.delete(); tmpFile = null; } catch (Throwable t) {};
			synchronized (compressedBuffers) {
				if (listener != null && listener.writingBuffer != null) {
					freeBuffer(listener.writingBuffer);
					listener.writingBuffer = null;
				}
			}
			listener = null;
		}
	}
	
	/* Write to Zip file */
	
	private WriteToZip writing = null;
	private TurnArray<WriteToZip> readyToWrite;
	private byte[] localEntryStart = new byte[] {
		'P','K',3,4, // local entry
		4,5, // version needed to extract, 4.5 allows ZIP64 extensions. TODO: if other compression than deflate, higher is needed
		0, 8, // general flags, set for each file. The bit 11 set means name is encoded using UTF-8 
		8, 0, // compression method = deflate
		0, 0, // time
		0, 0, // date
		0, 0, 0, 0, // crc-32
		0, 0, 0, 0, // compressed size
		0, 0, 0, 0, // uncompressed size
		0, 0, // name length
		0, 0 // extra length
	};
	
	private abstract class WriteToZip {
		public WriteToZip(ToZip file) {
			this.file = file;
		}
		protected ToZip file;
		public final void start() {
			if (logger.debug()) logger.debug("Start writing " + file.pathInZip);
			try { file.localHeaderOffset = output.getPosition(); }
			catch (IOException e) {
				fatalError(e);
				return;
			}
			// general flags
			// bit1+2 = maximum compression TODO change it when configurable
			// TODO bit 3 to 0 indicates that data descriptor is not set to 0, change it when using ZIP64
			localEntryStart[6] = 4;
			// TODO set date and time
			DataUtil.writeUnsignedIntegerLittleEndian(localEntryStart, 14, file.crc32);
			DataUtil.writeUnsignedIntegerLittleEndian(localEntryStart, 18, file.compressedSize);
			DataUtil.writeUnsignedIntegerLittleEndian(localEntryStart, 22, file.inputSize);
			byte[] name = file.pathInZip.getBytes(StandardCharsets.UTF_8);
			DataUtil.writeUnsignedShortLittleEndian(localEntryStart, 26, name.length);
			// TODO extra
			AsyncWork<Integer,IOException> writeEntry = output.writeAsync(ByteBuffer.wrap(localEntryStart));
			writeEntry.listenInline(new Runnable() {
				@Override
				public void run() {
					if (writeEntry.hasError()) {
						fatalError(writeEntry.getError());
						return;
					}
					AsyncWork<Integer,IOException> writeName = output.writeAsync(ByteBuffer.wrap(name));
					writeName.listenInline(new Runnable() {
						@Override
						public void run() {
							if (writeName.hasError()) {
								fatalError(writeName.getError());
								return;
							}
							startWriteContent();
						}
					});
				}
			});
			
		}
		protected abstract void startWriteContent();
	}
	
	private void addFileToWrite(WriteToZip toWrite) {
		if (logger.debug()) logger.debug("New file ready to be written: " + toWrite.file.pathInZip);
		synchronized (readyToWrite) {
			if (writing == null) {
				writing = toWrite;
			} else {
				readyToWrite.addLast(toWrite);
				return;
			}
		}
		writing.start();
	}
	
	private void nextFileToWrite() {
		writing.file.done = true;
		synchronized (readyToWrite) {
			writing = readyToWrite.pollFirst();
		}
		if (writing != null)
			writing.start();
		else
			checkEnd();
	}
	
	/* Write small file from memory */
	
	private class WriteToZipFromMemory extends WriteToZip {
		public WriteToZipFromMemory(ToZip file, Iterator<ByteBuffer> buffers) {
			super(file);
			this.buffers = buffers;
		}
		private Iterator<ByteBuffer> buffers;
		@Override
		protected void startWriteContent() {
			if (buffers.hasNext()) {
				ByteBuffer buffer = buffers.next();
				if (logger.debug()) logger.debug("Writing compressed data for " + file.pathInZip);
				AsyncWork<Integer,IOException> write = output.writeAsync(buffer);
				write.listenInline(new Runnable() {
					@Override
					public void run() {
						if (write.hasError()) {
							fatalError(write.getError());
							return;
						}
						if (logger.debug()) logger.debug(write.getResult()+" bytes written for " + file.pathInZip);
						freeBuffer(buffer.array());
						startWriteContent();
					}
				});
				return;
			}
			if (logger.debug()) logger.debug("Write done for " + file.pathInZip);
			nextFileToWrite();
		}
	}
	
	/* write large file from temporary file */
	
	private class WriteLargeFile extends WriteToZip {
		public WriteLargeFile(ToZip file, CompressFile compress) throws IOException {
			super(file);
			this.compress = compress;
			sizeTmp = compress.tmpIO.getPosition();
			sizeMemory = 0;
			for (ByteBuffer b : compress.compressedBuffers) sizeMemory += b.remaining();
			file.compressedSize = sizeTmp + sizeMemory;
			file.crc32 = compress.crc.getValue();
		}
		private CompressFile compress;
		private long sizeTmp;
		private long sizeMemory;
		private long outputStart;
		@Override
		protected void startWriteContent() {
			if (sizeMemory > 0) {
				// we can start writing data still in memory, so we can free those buffers sooner
				try { outputStart = output.getPosition(); }
				catch (IOException e) {
					fatalError(e);
					compress.closeAndCleanTmp();
					return;
				}
				writeFromMemory();
				return;
			}
			startWriteFromTmp();
		}
		private void writeFromMemory() {
			if (logger.debug()) logger.debug("Write memory part of large file: " + file.pathInZip);
			ByteBuffer buffer = compress.compressedBuffers.removeLast();
			sizeMemory -= buffer.remaining();
			AsyncWork<Integer,IOException> write = output.writeAsync(outputStart + sizeTmp + sizeMemory, buffer);
			write.listenInline(new Runnable() {
				@Override
				public void run() {
					if (write.hasError()) {
						fatalError(write.getError());
						return;
					}
					freeBuffer(buffer.array());
					if (sizeMemory > 0)
						writeFromMemory();
					else {
						AsyncWork<Long,IOException> seek = output.seekAsync(SeekType.FROM_BEGINNING, outputStart);
						seek.listenInline(new Runnable() {
							@Override
							public void run() {
								if (seek.hasError()) {
									fatalError(seek.getError());
									return;
								}
								startWriteFromTmp();
							}
						});
					}
				}
			});
		}
		private byte[] tmpBuffer;
		private void startWriteFromTmp() {
			AsyncWork<Long,IOException> seek = compress.tmpIO.seekAsync(SeekType.FROM_BEGINNING, 0);
			tmpBuffer = new byte[sizeTmp < 4*1024*1024 ? (int)sizeTmp : 4*1024*1024];
			seek.listenInline(new Runnable() {
				@Override
				public void run() {
					if (seek.hasError()) {
						fatalError(seek.getError());
						compress.closeAndCleanTmp();
						return;
					}
					writeFromTmp();
				}
			});
		}
		private void writeFromTmp() {
			if (logger.debug()) logger.debug("Reading temp file part of large file: " + file.pathInZip);
			AsyncWork<Integer,IOException> read = compress.tmpIO.readFullyAsync(ByteBuffer.wrap(tmpBuffer));
			read.listenInline(new Runnable() {
				@Override
				public void run() {
					if (read.hasError()) {
						fatalError(read.getError());
						compress.closeAndCleanTmp();
						return;
					}
					if (logger.debug()) logger.debug("Writing temp file part of large file: " + file.pathInZip);
					AsyncWork<Integer,IOException> write = output.writeAsync(ByteBuffer.wrap(tmpBuffer, 0, read.getResult().intValue()));
					write.listenInline(new Runnable() {
						@Override
						public void run() {
							if (write.hasError()) {
								fatalError(write.getError());
								compress.closeAndCleanTmp();
								return;
							}
							sizeTmp -= read.getResult().intValue();
							if (sizeTmp > 0)
								writeFromTmp();
							else {
								if (logger.debug()) logger.debug("End of write for large file: " + file.pathInZip);
								AsyncWork<Long,IOException> seek = output.seekAsync(SeekType.FROM_END, 0);
								compress.closeAndCleanTmp();
								seek.listenInline(new Runnable() {
									@Override
									public void run() {
										if (seek.hasError()) {
											fatalError(seek.getError());
											return;
										}
										nextFileToWrite();
									}
								});
							}
						}
					});
				}
			});
		}
	}
	
	/* Central Directory */
	
	private byte[] centralDirectoryEntry = {
		'P','K',1,2,
		63, 0, // version made by TODO check this
		4,5, // version needed
		0, 8, // general flags, set for each file. The bit 11 set means name is encoded using UTF-8 
		8, 0, // compression method = deflate
		0, 0, // time
		0, 0, // date
		0, 0, 0, 0, // crc-32
		0, 0, 0, 0, // compressed size
		0, 0, 0, 0, // uncompressed size
		0, 0, // name length
		0, 0, // extra length
		0, 0, // comment length
		0, 0, // disk number start
		0, 0, // internal file attributes
		0, 0, 0, 0, // external file attributes
		0, 0, 0, 0 // relative offset of local header
	};

	private void writeCentralDirectoryAndClose() {
		long pos;
		try { pos = output.getPosition(); }
		catch (IOException e) {
			fatalError(e);
			return;
		}
		if (logger.debug()) logger.debug("Start central directory at " + pos);
		writeNextCentralDirectoryEntry(pos, 0, 0);
	}
	private void writeNextCentralDirectoryEntry(long centralDirectoryPos, int fileIndex, int filesOk) {
		ToZip file = null;
		while (fileIndex < allFiles.size()) {
			file = allFiles.get(fileIndex);
			if (file.error == null) break;
			fileIndex++;
		}
		if (fileIndex == allFiles.size()) {
			writeEndOfCentralDirectory(centralDirectoryPos, filesOk);
			return;
		}
		// TODO date and time
		DataUtil.writeUnsignedIntegerLittleEndian(centralDirectoryEntry, 16, file.crc32);
		DataUtil.writeUnsignedIntegerLittleEndian(centralDirectoryEntry, 20, file.compressedSize);
		DataUtil.writeUnsignedIntegerLittleEndian(centralDirectoryEntry, 24, file.inputSize);
		byte[] name = file.pathInZip.getBytes(StandardCharsets.UTF_8);
		DataUtil.writeUnsignedShortLittleEndian(centralDirectoryEntry, 28, name.length);
		DataUtil.writeUnsignedIntegerLittleEndian(centralDirectoryEntry, 42, file.localHeaderOffset);
		if (logger.debug()) logger.debug("Write central directory entry for " + file.pathInZip);
		AsyncWork<Integer,IOException> write = output.writeAsync(ByteBuffer.wrap(centralDirectoryEntry));
		int index = fileIndex;
		write.listenInline(new Runnable() {
			@Override
			public void run() {
				if (write.hasError()) {
					fatalError(write.getError());
					return;
				}
				AsyncWork<Integer,IOException> writeName = output.writeAsync(ByteBuffer.wrap(name));
				writeName.listenInline(new Runnable() {
					@Override
					public void run() {
						if (writeName.hasError()) {
							fatalError(writeName.getError());
							return;
						}
						writeNextCentralDirectoryEntry(centralDirectoryPos, index+1, filesOk+1);
					}
				});
			}
		});
	}
	private void writeEndOfCentralDirectory(long centralDirectoryPos, int filesOk) {
		byte[] end = new byte[22];
		end[0] = 'P';
		end[1] = 'K';
		end[2] = 5;
		end[3] = 6;
		end[4] = 0;
		end[5] = 0;
		end[6] = 0;
		end[7] = 0;
		DataUtil.writeUnsignedShortLittleEndian(end, 8, filesOk);
		DataUtil.writeUnsignedShortLittleEndian(end, 10, filesOk);
		long pos;
		try { pos = output.getPosition(); }
		catch (IOException e) {
			fatalError(e);
			return;
		}
		DataUtil.writeUnsignedIntegerLittleEndian(end, 12, pos - centralDirectoryPos);
		DataUtil.writeUnsignedIntegerLittleEndian(end, 16, centralDirectoryPos);
		end[20] = 0;
		end[21] = 0;
		if (logger.debug()) logger.debug("Write end of central directory at " + pos);
		AsyncWork<Integer,IOException> write = output.writeAsync(ByteBuffer.wrap(end));
		write.listenInline(new Runnable() {
			@Override
			public void run() {
				if (write.hasError()) {
					fatalError(write.getError());
					return;
				}
				end();
			}
		});
	}
	
	/* The end */
	
	private void end() {
		if (logger.debug()) logger.debug("Close zip and end");
		try { output.close(); }
		catch (Exception e) {
			if (fatalError == null)
				fatalError = e;
		}
		if (fatalError != null)
			end.error(fatalError);
		else
			end.unblock();
	}

}
