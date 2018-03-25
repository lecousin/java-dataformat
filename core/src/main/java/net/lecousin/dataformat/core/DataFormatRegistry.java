package net.lecousin.dataformat.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.dataformat.core.DataFormatDetector.MoreThanHeaderNeeded;
import net.lecousin.dataformat.core.DataFormatDetector.OnlyHeaderNeeded;
import net.lecousin.dataformat.core.DataFormatDetector.Signature;
import net.lecousin.dataformat.core.formats.EmptyDataFormat;
import net.lecousin.dataformat.core.formats.UnknownDataFormat;
import net.lecousin.framework.collections.map.HalfByteHashMap;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.concurrent.synch.JoinPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.mutable.MutableInteger;
import net.lecousin.framework.progress.WorkProgress;

public class DataFormatRegistry {

	// *** Formats ***
	
	private static ArrayList<DataFormat> formats = new ArrayList<>();
	
	public static void registerFormat(DataFormat format) {
		synchronized (formats) {
			if (formats.contains(format)) throw new RuntimeException("DataFormat already registered: "+format);
			formats.add(format);
		}
	}

	static {
		registerFormat(EmptyDataFormat.instance);
	}
	
	public static List<DataFormat> getDataFormats() {
		return formats;
	}
	
	// *** Detectors ***
	
	private static HeaderRootNode root = new HeaderRootNode();
	
	public static void registerDetector(DataFormatDetector detector) {
		synchronized (formats) {
			for (DataFormat format : detector.getDetectedFormats())
				if (!formats.contains(format))
					throw new RuntimeException("DataFormat not registered: "+format);
		}
		synchronized (root) {
			Signature[] sig = detector.getHeaderSignature();
			if (sig == null || sig.length == 0) {
				if (detector instanceof MoreThanHeaderNeeded)
					root.otherwiseElsewhere.add((MoreThanHeaderNeeded)detector);
				else
					root.otherwise.add((OnlyHeaderNeeded)detector);
				return;
			}
			int sigIndex = 0;
			ArrayList<InsideHeader> previousInsideHeader = root.otherwiseHeader;
			if (sig[sigIndex].pos == 0) {
				// start at first byte
				HeaderNextByte next = root.map[sig[sigIndex].bytes[0]&0xFF];
				if (next == null)
					next = root.map[sig[sigIndex].bytes[0]&0xFF] = new HeaderNextByte();
				for (int i = 1; i < sig[sigIndex].bytes.length; ++i) {
					Object o = next.map.get(sig[sigIndex].bytes[i]);
					if (o == null) {
						if (i == sig[sigIndex].bytes.length-1 && sigIndex == sig.length-1) {
							// we reach the end of the signature => format detected
							next.map.put(sig[sigIndex].bytes[i], detector);
							return;
						}
						// we will continue on this signature
						HeaderNextByte n = new HeaderNextByte();
						next.map.put(sig[sigIndex].bytes[i], n);
						next = n;
					} else if (o instanceof HeaderNextByte) {
						// something already exist with the same start, let's continue
						next = (HeaderNextByte)o;
					} else if (o instanceof DataFormatDetector) {
						// a format was detected here
						HeaderNextByte n = new HeaderNextByte();
						n.detectorsToFinish.add((DataFormatDetector)o);
						if (i == sig[sigIndex].bytes.length-1 && sigIndex == sig.length-1) {
							// we reach the end
							n.detectorsToFinish.add(detector);
							return;
						}
						next = n;
					} else {
						throw new RuntimeException("Unexpected object "+o);
					}
				}
				previousInsideHeader = next.otherwiseHeader;
				sigIndex++;
			}
			do {
				InsideHeader inside = null;
				for (InsideHeader i : previousInsideHeader)
					if (i.pos == sig[sigIndex].pos) {
						inside = i;
						break;
					}
				if (inside == null) {
					inside = new InsideHeader();
					inside.pos = sig[sigIndex].pos;
					previousInsideHeader.add(inside);
				}
				// first byte
				Object o = inside.map.get(sig[sigIndex].bytes[0]);
				if (sig[sigIndex].bytes.length == 1 && sigIndex == sig.length-1) {
					// we are at the end of this signature
					if (o == null) {
						// we reach the end of the detection
						inside.map.put(sig[sigIndex].bytes[0], detector);
						return;
					}
					if (o instanceof DataFormatDetector) {
						// we had a previous format here
						HeaderNextByte next = new HeaderNextByte();
						next.detectorsToFinish.add((DataFormatDetector)o);
						next.detectorsToFinish.add(detector);
						inside.map.put(sig[sigIndex].bytes[0], next);
						return;
					}
					HeaderNextByte next = (HeaderNextByte)o;
					next.detectorsToFinish.add(detector);
					return;
				}
				// we have more bytes
				HeaderNextByte next;
				if (o == null) {
					next = new HeaderNextByte();
					inside.map.put(sig[sigIndex].bytes[0], next);
				} else if (o instanceof DataFormatDetector) {
					next = new HeaderNextByte();
					next.detectorsToFinish.add((DataFormatDetector)o);
					inside.map.put(sig[sigIndex].bytes[0], next);
				} else
					next = (HeaderNextByte)o;
				for (int i = 1; i < sig[sigIndex].bytes.length; ++i) {
					o = next.map.get(sig[sigIndex].bytes[i]);
					if (sigIndex == sig.length-1 && i == sig[sigIndex].bytes.length-1) {
						// end of this format
						if (o == null) {
							next.map.put(sig[sigIndex].bytes[i], detector);
							return;
						}
						if (o instanceof DataFormatDetector) {
							HeaderNextByte n = new HeaderNextByte();
							n.detectorsToFinish.add((DataFormatDetector)o);
							n.detectorsToFinish.add(detector);
							next.map.put(sig[sigIndex].bytes[i], n);
							return;
						}
						((HeaderNextByte)o).detectorsToFinish.add(detector);
						return;
					}
					if (o == null) {
						HeaderNextByte n = new HeaderNextByte();
						next.map.put(sig[sigIndex].bytes[i], n);
						next = n;
					} else if (o instanceof DataFormatDetector) {
						HeaderNextByte n = new HeaderNextByte();
						n.detectorsToFinish.add((DataFormatDetector)o);
						next.map.put(sig[sigIndex].bytes[i], n);
						next = n;
					} else
						next = (HeaderNextByte)o;
				}
				previousInsideHeader = next.otherwiseHeader;
				sigIndex++;
			} while (true);
		}
	}
	
	private static class HeaderRootNode {
		HeaderNextByte[] map = new HeaderNextByte[256];
		ArrayList<InsideHeader> otherwiseHeader = new ArrayList<>();
		ArrayList<DataFormatDetector.MoreThanHeaderNeeded> otherwiseElsewhere = new ArrayList<>();
		ArrayList<DataFormatDetector.OnlyHeaderNeeded> otherwise = new ArrayList<>();
	}
	private static class HeaderNextByte {
		// object can be null if nothing, another HeaderNextByte if we can continue, or a DataFormatDetector if we reach a single one
		HalfByteHashMap<Object> map = new HalfByteHashMap<Object>();
		ArrayList<InsideHeader> otherwiseHeader = new ArrayList<>();
		ArrayList<DataFormatDetector> detectorsToFinish = new ArrayList<>();
	}
	private static class InsideHeader {
		short pos;
		HalfByteHashMap<Object> map = new HalfByteHashMap<>();
	}
	
	static AsyncWork<DataFormat,IOException> detect(Data data, IO.Readable io, long dataSize, byte priority, byte[] buf, MutableInteger bufRead, WorkProgress progress, long work) {
		long stepDetect = work/20;
		long stepRead = work - stepDetect;
		AsyncWork<DataFormat,IOException> result = new AsyncWork<DataFormat,IOException>();
		ByteBuffer buffer = ByteBuffer.wrap(buf);
		AsyncWork<Integer,IOException> readTask = io.readFullyAsync(buffer);
		Task<Void,NoException> detectTask = new Task.Cpu<Void,NoException>("Data Format detection", priority) {
			@Override
			public Void run() {
				if (progress != null) progress.progress(stepRead);
				if (readTask.isCancelled()) {
					result.unblockCancel(readTask.getCancelEvent());
					return null;
				}
				if (!readTask.isSuccessful()) {
					bufRead.set(0);
					result.unblockError(readTask.getError());
					return null;
				}
				int len = readTask.getResult().intValue();
				if (len >= 0)
					bufRead.set(len);
				if (len <= 0) {
					if (progress != null) progress.progress(stepDetect);
					result.unblockSuccess(EmptyDataFormat.instance);
					return null;
				}
				
				LinkedList<DataFormatDetector> toFinish = new LinkedList<>();
				LinkedList<InsideHeader> insideHeaderToProcess = new LinkedList<>();
				LinkedList<MoreThanHeaderNeeded> toFinishOutside = new LinkedList<>();
				// start with root
				insideHeaderToProcess.addAll(root.otherwiseHeader);
				detectHeader(buf, 1, len, root.map[buf[0]&0xFF], toFinish, insideHeaderToProcess);
				do {
					if (isCancelled()) {
						result.unblockCancel(getCancelEvent());
						return null;
					}
					// process to finish
					while (!toFinish.isEmpty()) {
						DataFormatDetector detector = toFinish.removeLast();
						if (detector instanceof OnlyHeaderNeeded) {
							DataFormat detected = ((OnlyHeaderNeeded)detector).finishDetection(data, buf, len, dataSize);
							if (detected != null) {
								result.unblockSuccess(detected);
								if (progress != null) progress.progress(stepDetect);
								return null;
							}
						} else
							toFinishOutside.add((MoreThanHeaderNeeded)detector);
					}
					if (isCancelled()) {
						result.unblockCancel(getCancelEvent());
						return null;
					}
					// continue inside header
					if (!insideHeaderToProcess.isEmpty()) {
						InsideHeader inside = insideHeaderToProcess.removeLast();
						Object o = inside.map.get(buf[inside.pos]);
						if (o == null) continue;
						if (o instanceof DataFormatDetector) {
							if (o instanceof OnlyHeaderNeeded) {
								DataFormat detected = ((OnlyHeaderNeeded)o).finishDetection(data, buf, len, dataSize);
								if (detected != null) {
									result.unblockSuccess(detected);
									if (progress != null) progress.progress(stepDetect);
									return null;
								}
							} else
								toFinishOutside.add((MoreThanHeaderNeeded)o);
							continue;
						}
						HeaderNextByte next = (HeaderNextByte)o;
						detectHeader(buf, inside.pos+1, len, next, toFinish, insideHeaderToProcess);
						continue;
					}
					for (OnlyHeaderNeeded detector : root.otherwise) {
						DataFormat detected = detector.finishDetection(data, buf, len, dataSize);
						if (detected != null) {
							result.unblockSuccess(detected);
							if (progress != null) progress.progress(stepDetect);
							return null;
						}
					}
					break;
				} while (true);
				if (isCancelled()) {
					result.unblockCancel(getCancelEvent());
					return null;
				}
				toFinishOutside.addAll(root.otherwiseElsewhere);
				// TODO improve
				if (!toFinishOutside.isEmpty()) {
					IO.Readable.Seekable seekable;
					if (io instanceof IO.Readable.Seekable)
						seekable = (IO.Readable.Seekable)io;
					else {
						// TODO
						result.unblockSuccess(UnknownDataFormat.instance);
						if (progress != null) progress.progress(stepDetect);
						return null;
					}
					toFinishOutside.removeFirst().finishDetection(data, buf, len, seekable, dataSize).listenInline(new AsyncWorkListener<DataFormat, NoException>() {
						@Override
						public void ready(DataFormat detected) {
							if (detected != null) {
								result.unblockSuccess(detected);
								if (progress != null) progress.progress(stepDetect);
								return;
							}
							if (toFinishOutside.isEmpty()) {
								result.unblockSuccess(UnknownDataFormat.instance);
								if (progress != null) progress.progress(stepDetect);
								return;
							}
							toFinishOutside.removeFirst().finishDetection(data, buf, len, seekable, dataSize).listenInline(this);
						}
						@Override
						public void error(NoException error) {
							ready(null);
						}
						@Override
						public void cancelled(CancelException event) {
							result.unblockCancel(event);
						}
					});
					return null;
				}
				result.unblockSuccess(UnknownDataFormat.instance);
				if (progress != null) progress.progress(stepDetect);
				return null;
			}
		};
		detectTask.startOn(readTask, true);
		result.onCancel(new Listener<CancelException>() {
			@Override
			public void fire(CancelException event) {
				readTask.unblockCancel(event);
				detectTask.cancel(event);
			}
		});
		return result;
	}
	
	private static void detectHeader(byte[] header, int pos, int len, HeaderNextByte next, LinkedList<DataFormatDetector> toFinish, LinkedList<InsideHeader> insideHeaderToProcess) {
		if (next == null) return;
		toFinish.addAll(next.detectorsToFinish);
		insideHeaderToProcess.addAll(next.otherwiseHeader);
		while (pos < len) {
			Object o = next.map.get(header[pos]);
			if (o == null) break;
			if (o instanceof DataFormatDetector) {
				toFinish.add((DataFormatDetector)o);
				break;
			}
			next = (HeaderNextByte)o;
			toFinish.addAll(next.detectorsToFinish);
			insideHeaderToProcess.addAll(next.otherwiseHeader);
			pos++;
		}
	}
	
	// *** Specializations ***
	
	private static Map<DataFormat,ArrayList<DataFormatSpecializationDetector>> specializations = new HashMap<>(25);
	
	public static void registerSpecializationDetector(DataFormatSpecializationDetector detector) {
		DataFormat base = detector.getBaseFormat();
		DataFormat[] specialized = detector.getDetectedFormats();
		synchronized (formats) {
			if (!formats.contains(base))
				throw new RuntimeException("DataFormat not registered: "+base);
			for (DataFormat f : specialized)
				if (!base.getClass().isAssignableFrom(f.getClass()))
					throw new RuntimeException("DataFormat "+f.getClass().getName()+" should extend "+base.getClass().getName());
				else if (!formats.contains(f))
					throw new RuntimeException("DataFormat not registered: "+f);
		}
		synchronized (specializations) {
			ArrayList<DataFormatSpecializationDetector> list = specializations.get(base);
			if (list == null) {
				list = new ArrayList<>();
				specializations.put(base, list);
			}
			list.add(detector);
		}
	}
	
	static void detectSpecializations(Data data, DataFormat base, byte[] header, int headerSize, byte priority, DataFormatListener listener, WorkProgress progress, long work) {
		Task<Void,NoException> task = new Task.Cpu<Void,NoException>("DataFormat specialization detection", priority) {
			@Override
			public Void run() {
				ArrayList<DataFormatSpecializationDetector> list;
				synchronized (specializations) {
					list = specializations.get(base);
				}
				if (list == null) {
					if (progress != null) progress.progress(work);
					listener.endOfDetection(data);
					return null;
				}
				JoinPoint<NoException> jp = new JoinPoint<>();
				jp.addToJoin(list.size());
				jp.start();
				jp.listenInline(new Runnable() {
					@Override
					public void run() {
						listener.endOfDetection(data);
					}
				});
				int nb = list.size();
				long w = work;
				for (DataFormatSpecializationDetector detector : list) {
					long step = w / nb--;
					w -= step;
					AsyncWork<DataFormat,NoException> sp = detector.detectSpecialization(data, priority, header, headerSize);
					if (sp == null) {
						if (progress != null) progress.progress(step);
						jp.joined();
						continue;
					}
					DataFormatListener subListener = new DataFormatListener() {
						@Override
						public void formatDetected(Data data, DataFormat format) {
							listener.formatDetected(data, format);
						}
						@Override
						public void endOfDetection(Data data) {
							jp.joined();
						}
						@Override
						public void detectionError(Data data, Exception error) {
							jp.joined();
						}
						
						@Override
						public void detectionCancelled(Data data) {
							jp.joined();
						}
					};
					if (sp.isUnblocked()) {
						DataFormat spec = sp.getResult();
						if (spec != null) {
							listener.formatDetected(data, spec);
							detectSpecializations(data, spec, header, headerSize, priority, subListener, progress, step);
							continue;
						}
						if (progress != null) progress.progress(step);
						jp.joined();
						continue;
					}
					sp.listenInline(new AsyncWorkListener<DataFormat,NoException>() {
						@Override
						public void ready(DataFormat spec) {
							if (spec != null) {
								listener.formatDetected(data, spec);
								detectSpecializations(data, spec, header, headerSize, priority, subListener, progress, step);
								return;
							}
							if (progress != null) progress.progress(step);
							jp.joined();
						}
						@Override
						public void error(NoException error) {
							jp.joined();
						}
						@Override
						public void cancelled(CancelException event) {
							jp.joined();
						}
					});
				}
				return null;
			}
		};
		task.start();
	}
	
}
