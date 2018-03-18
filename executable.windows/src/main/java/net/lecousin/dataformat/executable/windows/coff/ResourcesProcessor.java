package net.lecousin.dataformat.executable.windows.coff;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.FragmentedSubData;
import net.lecousin.dataformat.core.SubData;
import net.lecousin.dataformat.image.bmp.DIBReaderOp.DIBImageProvider;
import net.lecousin.dataformat.image.ico.ICOCURFormat.ICOImageProvider;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.util.Pair;

public class ResourcesProcessor {

	public static void process(Map<Object,Object> map, ArrayList<Data> list, byte[] b) throws Exception {
		processGroupIcon(map, 0x0E, 0x03, "Icon Group", b, list);
		processGroupIcon(map, 0x0C, 0x01, "Cursor Group", b, list);
		processType(new Integer(6), map, new DataTypeSetter(ResourceDataType_StringTable.instance));
		processType(new Integer(9), map, new DataTypeSetter(ResourceDataType_Accelerators.instance));
		processType(new Integer(4), map, new DataTypeSetter(ResourceDataType_Menu.instance));
		processType(new Integer(5), map, new DataTypeSetter(ResourceDataType_Dialog.instance));
		processType(new Integer(11), map, new DataTypeSetter(ResourceDataType_MessageTable.instance));
		processType(new Integer(24), map, new DataTypeSetter(ResourceDataType_Manifest.instance));
		processType("LSTR", map, new DataTypeSetter(ResourceDataType_LSTR.instance));
		processType(new Integer(3), map, new ICOReaderSetter());
		processType(new Integer(1), map, new ICOReaderSetter());
		processMap(map, list);
	}
	
	private static class DataTypeSetter implements Listener<Data> {
		public DataTypeSetter(DataFormat format) {
			this.format = format;
		}
		private DataFormat format;
		@Override
		public void fire(Data data) {
			data.setFormat(format);
		}
	}
	
	private static class ICOReaderSetter implements Listener<Data> {
		@Override
		public void fire(Data data) {
			if (!data.hasProperty(DIBImageProvider.DATA_PROPERTY))
				data.setProperty(DIBImageProvider.DATA_PROPERTY, new ICOImageProvider(-1));
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void processType(Object type, Map<Object,Object> map, Listener<Data> listener) {
		Object o = map.get(type);
		if (o == null) return;
		if (!(o instanceof Map)) return;
		processTypeMap((Map<Object,Object>)o, listener);
	}
	@SuppressWarnings("unchecked")
	private static void processTypeMap(Map<Object,Object> map, Listener<Data> listener) {
		for (Object o : map.values()) {
			if (o instanceof Data) listener.fire((Data)o);
			else if (o instanceof Map) processTypeMap((Map<Object,Object>)o, listener);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void processMap(Map<Object,Object> map, ArrayList<Data> list) {
		for (Object o : map.values()) {
			if (o instanceof Data) list.add((Data)o);
			else if (o instanceof Map) processMap((Map<Object,Object>)o, list);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void processGroupIcon(Map<Object,Object> map, int group_code, int ico_code, String group_name, byte[] file_header, ArrayList<Data> datas) 
	throws Exception {
		Object group_icon = map.remove(new Integer(group_code));
		Object icons = map.get(new Integer(ico_code));
		if (group_icon instanceof Map && icons instanceof Map) {
			for (Map.Entry<Object, Object> e : ((Map<Object,Object>)group_icon).entrySet()) {
				Object name = e.getKey();
				Object v = e.getValue();
				if (v instanceof Map) {
					for (Map.Entry<Object,Object> e2 : ((Map<Object,Object>)v).entrySet()) {
						Object lang = e2.getKey();
						Object gi = e2.getValue();
						if (gi instanceof SubData) {
							SubData gi_data = (SubData)gi;
							AsyncWork<? extends IO.Readable.Seekable,Exception> open = gi_data.openReadOnly(Task.PRIORITY_NORMAL);
							@SuppressWarnings("resource")
							IO.Readable.Seekable io = open.blockResult(0);
							try {
								io.readFullySync(0, ByteBuffer.wrap(file_header,0,6));
								int nb_ico = DataUtil.readUnsignedShortLittleEndian(file_header, 4);
								List<Pair<byte[],SubData>> found_icons = new LinkedList<Pair<byte[],SubData>>();
								for (int i = 0; i < nb_ico; ++i) {
									byte[] ico_header = new byte[16];
									io.readFullySync(6+i*14, ByteBuffer.wrap(ico_header, 0, 14));
									int icon_id = DataUtil.readUnsignedShortLittleEndian(ico_header, 12);
									Object ico_map = ((Map<Object,Object>)icons).get(new Integer(icon_id));
									if (ico_map instanceof Map) {
										Object ico_obj = ((Map<Object,Object>)ico_map).get(lang);
										if (ico_obj instanceof SubData) {
											found_icons.add(new Pair<byte[],SubData>(ico_header, (SubData)ico_obj));
											((Map<Object,Object>)ico_map).remove(lang);
										}
									}
								}
								if (!found_icons.isEmpty()) {
									byte[] buffer = new byte[6+found_icons.size()*16];
									System.arraycopy(file_header, 0, buffer, 0, 4);
									DataUtil.writeShortLittleEndian(buffer, 4, (short)found_icons.size());
									long pos_data = 6+found_icons.size()*16;
									int pos_header = 6;
									for (Pair<byte[],SubData> ico : found_icons) {
										byte[] ico_header = ico.getValue1();
										SubData ico_data = ico.getValue2();
										System.arraycopy(ico_header, 0, buffer, pos_header, 12);
										DataUtil.writeIntegerLittleEndian(buffer, pos_header+12, (int)pos_data);
										pos_header += 16;
										pos_data += ico_data.getSize();
									}
									FragmentedSubData sd = new FragmentedSubData(gi_data.getParent(), group_name+" "+name.toString());
									sd.addHeader(buffer);
									for (Pair<byte[],SubData> ico : found_icons) {
										SubData ico_data = ico.getValue2();
										sd.addFragment(ico_data.getOffset(), ico_data.getSize());
									}
									datas.add(sd);
								}
							} finally {
								io.closeAsync();
							}
						}
					}
				}
			}
		}
	}
	
}
