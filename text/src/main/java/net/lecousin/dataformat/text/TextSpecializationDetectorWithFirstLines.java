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
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.util.UnprotectedString;

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
		public DataFormat detect(Data data, ArrayList<UnprotectedString> lines, char[] allHeaderChars, int nbHeaderChars);
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
	public AsyncWork<DataFormat, NoException> detectSpecialization(Data data, byte priority, byte[] header, int headerSize) {
		Task.Cpu<DataFormat, NoException> task = new Task.Cpu<DataFormat,NoException>("Detect text format", priority) {
			@Override
			public DataFormat run() {
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
				ArrayList<UnprotectedString> lines = new ArrayList<>();
				int start = 0;
				for (int i = 0; i < nb; ++i) {
					if (chars[i] == '\r') {
						if (i < nb-1 && chars[i+1] == '\n') {
							lines.add(new UnprotectedString(chars, start, i-start, i-start));
							start = i+2;
							i++;
							continue;
						}
						lines.add(new UnprotectedString(chars, start, i-start, i-start));
						start = i+1;
						continue;
					}
					if (chars[i] == '\n') {
						lines.add(new UnprotectedString(chars, start, i-start, i-start));
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
			}
		};
		task.start();
		return task.getOutput();
	}
	
}
