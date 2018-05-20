package net.lecousin.dataformat.executable.windows.coff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.dataformat.core.ContainerDataFormat;
import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.executable.windows.WinExeDataFormatPlugin;
import net.lecousin.framework.collections.CollectionListener;
import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.AsyncWork.AsyncWorkListener;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.locale.LocalizableStringBuffer;
import net.lecousin.framework.math.FragmentedRangeLong;
import net.lecousin.framework.math.RangeLong;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.uidescription.resources.IconProvider;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.StringUtil;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class COFFDataFormat implements ContainerDataFormat {

	public static final COFFDataFormat instance = new COFFDataFormat();
	
	private static Log logger = LogFactory.getLog(COFFDataFormat.class);
	
	@Override
	public ILocalizableString getName() {
		return new FixedLocalizedString("Microsoft Common Object");
	}
	
	@Override
	public IconProvider getIconProvider() { return WinExeDataFormatPlugin.winIconProvider; }
	
	@Override
	public String[] getFileExtensions() {
		return new String[] {};
	}
	
	@Override
	public String[] getMIMETypes() {
		return new String[] {};
	}
	
	@Override
	public AsyncWork<COFFInfo, Exception> getInfo(Data data, byte priority) {
		AsyncWork<COFFInfo,Exception> result = new AsyncWork<>();
		AsyncWork<? extends IO.Readable.Seekable,Exception> open = data.openReadOnly(priority);
		open.listenInline(new Runnable() {
			@Override
			public void run() {
				if (!open.isSuccessful()) {
					if (open.isCancelled())
						result.unblockCancel(open.getCancelEvent());
					else
						result.unblockError(open.getError());
					return;
				}
				@SuppressWarnings("resource")
				IO.Readable.Seekable io = open.getResult();
				byte[] buf = new byte[20];
				io.readAsync(0, ByteBuffer.wrap(buf)).listenInline(new AsyncWorkListener<Integer, IOException>() {
					@Override
					public void ready(Integer nb) {
						if (nb.intValue() != 20) {
							result.unblockError(new Exception("Invalid COFF data: less than 20 bytes"));
							try { io.close(); } catch (Throwable t) {}
							return;
						}
						COFFInfo info = new COFFInfo();
						info.targetMachine = COFFInfo.TargetMachine.get(DataUtil.readUnsignedShortLittleEndian(buf, 0));
						// TODO
						result.unblockSuccess(info);
						try { io.close(); } catch (Throwable t) {}
					}
					@Override
					public void error(IOException error) {
						result.unblockError(error);
						io.closeAsync();
					}
					@Override
					public void cancelled(CancelException event) {
						result.unblockCancel(event);
						io.closeAsync();
					}
				});
			}
		});
		return result;
	}
	
	@Override
	public WorkProgress listenSubData(Data container, CollectionListener<Data> listener) {
		AsyncWork<? extends IO.Readable.Seekable,Exception> open = container.openReadOnly(Task.PRIORITY_NORMAL);
		WorkProgress progress = new WorkProgressImpl(10000, "Reading COFF sections");
		open.listenInline(
			(io) -> {
				progress.progress(500);
				byte[] buf = new byte[40];
				AsyncWork<Integer,IOException> read = io.readAsync(0, ByteBuffer.wrap(buf,0,20));
				read.listenInline(() -> {
					if (read.hasError()) {
						listener.error(read.getError());
						progress.error(read.getError());
						io.closeAsync();
						return;
					}
					if (read.isCancelled()) {
						listener.elementsReady(new ArrayList<>(0));
						progress.done();
						io.closeAsync();
						return;
					}
					int nb_sections = DataUtil.readUnsignedShortLittleEndian(buf, 2);
					int size_opt_headers = DataUtil.readUnsignedShortLittleEndian(buf, 16);
					long pos = 20+size_opt_headers;
					progress.progress(200);
					readSection(container, ((Long)container.getProperty("COFFOffset")).longValue(), io, 0, nb_sections, pos, buf, new ArrayList<Section>(nb_sections), -1, listener, progress, 10000 - 500 - 200 - 300);
				});
			},
			(error) -> {
				listener.error(error);
				progress.error(error);
			},
			(cancel) -> {
				listener.elementsReady(new ArrayList<>(0));
				progress.done();
			}
		);
		return progress;
	}
	
	@Override
	public void unlistenSubData(Data container, CollectionListener<Data> listener) {
	}
	
	private static class Section {
		String name;
		long virtual_addr, virtual_size;
		long real_addr, real_size;
	}
	private void readSection(Data data, long offset, IO.Readable.Seekable io, int sectionIndex, int nbSections, long pos, byte[] buf, ArrayList<Section> sections, long min_section_start, CollectionListener<Data> listener, WorkProgress progress, long work) {
		if (sectionIndex == nbSections) {
			Task.Cpu<Void, NoException> task = new Task.Cpu<Void, NoException>("Reading COFF sections", Task.PRIORITY_NORMAL) {
				@Override
				public Void run() {
					FragmentedRangeLong unused_data = min_section_start > 0 ? new FragmentedRangeLong(new RangeLong(min_section_start, data.getSize()-1)) : new FragmentedRangeLong();
					ArrayList<Data> datas = new ArrayList<>(sections.size()+2);
					for (Section s : sections) {
						unused_data.removeRange(s.real_addr, s.real_addr+s.real_size-1);
						if (s.name.equals(".rsrc")) {
							try { readRSRC(data, io, s, sections, buf, datas); }
							catch (Exception e) {
								if (logger.isErrorEnabled())
									logger.error("Error reading RSRC", e);
							}
						} else
							datas.add(new SubData(data, s.real_addr+s.virtual_size, s.real_size, new FixedLocalizedString(s.name)));
					}
					int i = 1;
					for (RangeLong r : unused_data) {
						SubData hidden = new SubData(data, r.min, r.max-r.min+1, new LocalizableStringBuffer(new LocalizableString("dataformat.executable.windows", "Hidden data"), " "+(i++)));
						datas.add(hidden);
					}
					listener.elementsReady(datas);
					progress.done();
					io.closeAsync();
					return null;
				}
			};
			task.start();
			return;
		}
		long step = work / (nbSections - sectionIndex);
		AsyncWork<Integer,IOException> read = io.readAsync(pos, ByteBuffer.wrap(buf));
		read.listenInline(new Runnable() {
			@Override
			public void run() {
				if (!read.isSuccessful()) {
					readSection(data, offset, io, nbSections, nbSections, pos, buf, sections, min_section_start, listener, progress, work);
					return;
				}
				int j;
				long min = min_section_start;
				for (j = 0; j < 9 && buf[j] != 0; ++j);
				Section s = new Section();
				s.name = new String(buf, 0, j, StandardCharsets.UTF_8);
				s.virtual_size = DataUtil.readUnsignedIntegerLittleEndian(buf, 8);
				s.virtual_addr = DataUtil.readUnsignedIntegerLittleEndian(buf, 12);
				s.real_size = DataUtil.readUnsignedIntegerLittleEndian(buf, 16);
				s.real_addr = DataUtil.readUnsignedIntegerLittleEndian(buf, 20);
				s.real_addr -= offset;
				if (s.real_size > s.virtual_size) s.real_size = s.virtual_size;
				if (s.real_size > 0)
					if (min == -1 || s.real_addr < min) min = s.real_addr;
				sections.add(s);
				progress.progress(step);
				readSection(data, offset, io, sectionIndex+1, nbSections, pos+40, buf, sections, min, listener, progress, work - step);
			}
		});
	}
	
	@SuppressWarnings("unchecked")
	private void readRSRC(Data data, IO.Readable.Seekable io, Section rsrc, ArrayList<Section> sections, byte[] b, ArrayList<Data> datas) throws Exception {
		Map<Object,Object> map = new LinkedHashMap<Object,Object>();
		readRSRCTable(data, io, rsrc, rsrc.real_addr, map, b);
		for (Object type : map.keySet()) {
			Object v1 = map.get(type);
			if (v1 instanceof Pair) {
				Pair<Long,Long> p = (Pair<Long,Long>)v1;
				long addr = getRealAddr(p.getValue1().longValue(), sections);
				if (addr == -1) {
					if (logger.isErrorEnabled())
						logger.error("Invalid virtual address "+StringUtil.encodeHexaPadding(p.getValue1().longValue())+" in "+data.getDescription());
				} else if (addr != -2)
					map.put(type, new SubData(data, addr, p.getValue2().longValue(), new FixedLocalizedString("type "+type.toString())));
				continue;
			}
			Map<Object,Object> map2 = (Map<Object,Object>)v1;
			for (Object name : map2.keySet()) {
				Object v2 = map2.get(name);
				if (v2 instanceof Pair) {
					Pair<Long,Long> p = (Pair<Long,Long>)v2;
					long addr = getRealAddr(p.getValue1().longValue(), sections);
					if (addr == -1) {
						if (logger.isErrorEnabled())
							logger.error("Invalid virtual address "+StringUtil.encodeHexaPadding(p.getValue1().longValue())+" in "+data.getDescription());
					} else if (addr != -2)
						map2.put(name, new SubData(data, addr, p.getValue2().longValue(), new FixedLocalizedString("Resource type "+type.toString()+" name "+name.toString())));
					continue;
				}
				Map<Object,Object> map3 = (Map<Object,Object>)v2;
				for (Object lang : map3.keySet()) {
					Object v3 = map3.get(lang);
					if (v3 instanceof Pair) {
						Pair<Long,Long> p = (Pair<Long,Long>)v3;
						long addr = getRealAddr(p.getValue1().longValue(), sections);
						if (addr == -1) {
							if (logger.isErrorEnabled())
								logger.error("Invalid virtual address "+StringUtil.encodeHexaPadding(p.getValue1().longValue())+" in "+data.getDescription());
						} else if (addr != -2)
							map3.put(lang, new SubData(data, addr, p.getValue2().longValue(), new FixedLocalizedString("Resource type "+type.toString()+" name "+name.toString()+" language "+lang.toString())));
						continue;
					}
					System.out.println("Unexpected PE resource level");
				}
			}
		}
		ResourcesProcessor.process(map, datas, b);
	}
	private void readRSRCTable(Data data, IO.Readable.Seekable io, Section rsrc, long table_start, Map<Object,Object> map, byte[]b) {
		try {
			io.readFullySync(table_start, ByteBuffer.wrap(b, 0, 16));
			if (b[0] != 0 || b[1] != 0 || b[2] != 0 || b[3] != 0) {
				if (logger.isErrorEnabled())
					logger.error("Invalid RSRC Table at position "+StringUtil.encodeHexaPadding(table_start)+" [RSRC starts at "+StringUtil.encodeHexaPadding(rsrc.real_addr)+" ("+data.getDescription()+"): "+StringUtil.encodeHexa(b, 0, 4));
				return;
			}
			int nb_names = DataUtil.readUnsignedShortLittleEndian(b, 12);
			int nb_ids = DataUtil.readUnsignedShortLittleEndian(b, 14);
			ArrayList<Pair<Long,Long>> names = new ArrayList<>(nb_names);
			for (int i = 0; i < nb_names; ++i) {
				if (io.readFullySync(table_start+16+i*8, ByteBuffer.wrap(b,0,8)) != 8) {
					if (logger.isErrorEnabled())
						logger.error("Invalid RSRC Table: name "+(i+1)+" cannot be read at "+StringUtil.encodeHexaPadding(table_start+16+i*8));
					return;
				}
				long name_addr = DataUtil.readIntegerLittleEndian(b, 0) & 0x7FFFFFFF;
				long addr = DataUtil.readUnsignedIntegerLittleEndian(b, 4);
				names.add(new Pair<>(new Long(name_addr), new Long(addr)));
			}
			ArrayList<Pair<Integer,Long>> ids = new ArrayList<>(nb_ids);
			for (int i = 0; i < nb_ids; ++i) {
				if (io.readFullySync(table_start+16+(nb_names+i)*8, ByteBuffer.wrap(b,0,8)) != 8) {
					if (logger.isErrorEnabled())
						logger.error("Invalid RSRC Table: id "+(i+1)+" cannot be read at "+StringUtil.encodeHexaPadding(table_start+16+(nb_names+i)*8));
					return;
				}
				int id = DataUtil.readIntegerLittleEndian(b, 0);
				long addr = DataUtil.readUnsignedIntegerLittleEndian(b, 4);
				ids.add(new Pair<>(new Integer(id), new Long(addr)));
			}
			
			List<Pair<Object,Long>> sub_tables = new LinkedList<Pair<Object,Long>>();
			for (Pair<Long,Long> name : names) {
				if (io.readFullySync(rsrc.real_addr+name.getValue1().longValue(), ByteBuffer.wrap(b,0,2)) != 2) {
					if (logger.isErrorEnabled())
						logger.error("Invalid RSC Table: name cannot be read at "+(rsrc.real_addr+name.getValue1().longValue()));
					continue;
				}
				int name_len = DataUtil.readUnsignedShortLittleEndian(b, 0);
				int read = 0;
				StringBuilder s = new StringBuilder();
				while (read < name_len) {
					int nb = io.readFullySync(rsrc.real_addr+name.getValue1().longValue()+2+read, ByteBuffer.wrap(b,0,name_len-read > b.length ? b.length : name_len-read));
					read += nb;
					for (int ic = 0; ic < nb; ic+=2) {
						char c = (char)((b[ic] & 0xFF) | ((b[ic+1] & 0xFF) << 8));
						s.append(c);
					}
					if (nb < (name_len-read > b.length ? b.length : name_len-read)) {
						if (logger.isErrorEnabled())
							logger.error("Invalid RSC Table: name is truncated at "+(rsrc.real_addr+name.getValue1().longValue()));
						break;
					}
				}
				if ((name.getValue2().longValue() & 0x80000000) == 0) {
					if (io.readFullySync(rsrc.real_addr+name.getValue2().longValue(), ByteBuffer.wrap(b,0,16)) != 16) {
						if (logger.isErrorEnabled())
							logger.error("Invalid RSC Table: name cannot be read at its address "+(rsrc.real_addr+name.getValue2().longValue()));
						continue;
					}
					long res_addr = DataUtil.readUnsignedIntegerLittleEndian(b, 0);
					long res_size = DataUtil.readUnsignedIntegerLittleEndian(b, 4);
					map.put(s.toString(), new Pair<Long,Long>(new Long(res_addr), new Long(res_size)));
				} else {
					long table_addr = name.getValue2().longValue() & 0x7FFFFFFF;
					if (table_addr < 0) {
						if (logger.isErrorEnabled())
							logger.error("Invalid RSRC Table ("+data.getDescription()+"): Invalid sub table address: "+StringUtil.encodeHexaPadding(table_addr));
					} else {
						sub_tables.add(new Pair<Object,Long>(s.toString(), new Long(table_addr)));
					}
				}

			}
			
			for (Pair<Integer,Long> id : ids) {
				if ((id.getValue2().longValue() & 0x80000000) == 0) {
					if (io.readFullySync(rsrc.real_addr+id.getValue2().longValue(), ByteBuffer.wrap(b,0,16)) != 16) {
						if (logger.isErrorEnabled())
							logger.error("Invalid RSC Table: id cannot be read at its address "+(rsrc.real_addr+id.getValue2().longValue()));
						continue;
					}
					long res_addr = DataUtil.readUnsignedIntegerLittleEndian(b, 0);
					long res_size = DataUtil.readUnsignedIntegerLittleEndian(b, 4);
					map.put(id.getValue1(), new Pair<Long,Long>(new Long(res_addr), new Long(res_size)));
				} else {
					long table_addr = id.getValue2().longValue() & 0x7FFFFFFF;
					if (table_addr < 0) {
						if (logger.isErrorEnabled())
							logger.error("Invalid RSRC Table ("+data.getDescription()+"): Invalid sub table address: "+StringUtil.encodeHexaPadding(table_addr));
						break;
					}
					sub_tables.add(new Pair<Object,Long>(id.getValue1(), new Long(table_addr)));
				}
			}
			
			for (Pair<Object,Long> p : sub_tables) {
				Map<Object,Object> submap = new LinkedHashMap<Object,Object>();
				readRSRCTable(data, io, rsrc, rsrc.real_addr+p.getValue2().longValue(), submap, b);
				map.put(p.getValue1(), submap);
			}
		} catch (IOException e) {
			if (logger.isErrorEnabled())
				logger.error("Error reading .rsrc table for "+data.getDescription(), e);
		}
	}
	private static long getRealAddr(long virt, List<Section> sections) {
		for (int i = sections.size()-1; i >= 0; --i) {
			Section s = sections.get(i);
			if (virt > s.virtual_addr && virt < s.virtual_addr+s.virtual_size) {
				if (virt < s.virtual_addr+s.real_size)
					return virt-s.virtual_addr+s.real_addr;
				return -2;
			}
		}
		return -1;
	}
	
	@Override
	public Class<? extends DataCommonProperties> getSubDataCommonProperties() {
		return null;
	}
	
	@Override
	public DataCommonProperties getSubDataCommonProperties(Data subData) {
		return null;
	}

}
