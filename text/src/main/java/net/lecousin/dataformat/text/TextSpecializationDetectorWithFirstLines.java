package net.lecousin.dataformat.text;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Collection;

import net.lecousin.dataformat.core.Data;
import net.lecousin.dataformat.core.DataFormat;
import net.lecousin.dataformat.core.DataFormatRegistry;
import net.lecousin.dataformat.core.DataFormatSpecializationDetector;
import net.lecousin.framework.concurrent.async.AsyncSupplier;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.text.CharArrayString;

public class TextSpecializationDetectorWithFirstLines implements DataFormatSpecializationDetector {

	public static class ExtensionPoint implements net.lecousin.framework.plugins.ExtensionPoint<Plugin> {
		
		@Override
		public Class<Plugin> getPluginClass() {
			return Plugin.class;
		}
		
		private static ArrayList<Plugin> plugins = new ArrayList<>();
		
		@Override
		public void addPlugin(Plugin plugin) {
			plugins.add(plugin);
		}
		
		@Override
		public void allPluginsLoaded() {
			DataFormatRegistry.registerSpecializationDetector(new TextSpecializationDetectorWithFirstLines());
		}
		
		@Override
		public Collection<Plugin> getPlugins() {
			return plugins;
		}
		
	}
	
	public static interface Plugin extends net.lecousin.framework.plugins.Plugin {
		public DataFormat[] getDetectedFormats();
		public DataFormat detect(Data data, ArrayList<CharArrayString> lines, char[] allHeaderChars, int nbHeaderChars);
	}
	
	@Override
	public DataFormat getBaseFormat() {
		return TextDataFormat.instance;
	}
	
	@Override
	public DataFormat[] getDetectedFormats() {
		ArrayList<DataFormat> formats = new ArrayList<>();
		for (Plugin pi : ExtensionPoint.plugins)
			for (DataFormat f : pi.getDetectedFormats())
				formats.add(f);
		return formats.toArray(new DataFormat[formats.size()]);
	}
	
	@Override
	public AsyncSupplier<DataFormat, NoException> detectSpecialization(Data data, Priority priority, byte[] header, int headerSize) {
		Task<DataFormat, NoException> task = Task.cpu("Detect text format", priority, taskContext -> {
			// convert to characters
			Charset cs = (Charset)data.getProperty(TextDataFormat.PROPERTY_CHARSET);
			char[] chars = null;
			int nb = 0;
			if (cs != null) {
				CharsetDecoder decoder = cs.newDecoder();
				try {
					CharBuffer cb = decoder.decode(ByteBuffer.wrap(header, 0, headerSize));
					chars = cb.array();
					nb = cb.limit();
				} catch (Throwable t) {}
			}
			if (chars == null) {
				chars = new char[headerSize];
				nb = headerSize;
				for (int i = 0; i < headerSize; ++i)
					chars[i] = (char)(header[i]&0xFF);
			}
			// split lines
			ArrayList<CharArrayString> lines = new ArrayList<>();
			int start = 0;
			for (int i = 0; i < nb; ++i) {
				if (chars[i] == '\r') {
					if (i < nb-1 && chars[i+1] == '\n') {
						lines.add(new CharArrayString(chars, start, i-start, i-start));
						start = i+2;
						i++;
						continue;
					}
					lines.add(new CharArrayString(chars, start, i-start, i-start));
					start = i+1;
					continue;
				}
				if (chars[i] == '\n') {
					lines.add(new CharArrayString(chars, start, i-start, i-start));
					start = i+1;
				}
			}
			if (lines.isEmpty())
				return null;
			for (Plugin pi : ExtensionPoint.plugins) {
				DataFormat f = pi.detect(data, lines, chars, nb);
				if (f != null)
					return f;
			}
			return null;
		});
		task.start();
		return task.getOutput();
	}
	
}
