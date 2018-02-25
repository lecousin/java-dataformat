package net.lecousin.dataformat.executable.windows.versioninfo;

import java.util.HashMap;
import java.util.Map;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.ui_description.annotations.name.FixedName;

public class VersionInfo implements DataFormatInfo {

	@FixedName("Type") // TODO
	public Type type;
	@FixedName("Sub-type")
	public SubType sub_type;
	
	public Map<Integer,Map<String,String>> language_strings = new HashMap<Integer,Map<String,String>>();
	
	@FixedName("Product Name")
	public String productName;
	@FixedName("Product Version")
	public String productVersion;
	@FixedName("Publisher")
	public String publisher;
	@FixedName("Description")
	public String description;
	
	public static enum Type {
		Application,
		Dynamic_Library,
		Static_Library,
		Driver,
		Font,
		VirtualDevice,
	}
	public static interface SubType{}
	public static enum DriverType implements SubType {
		Communications,
		Display,
		Installable,
		Keyboard,
		Language,
		Mouse,
		Network,
		Printer,
		Sound,
		System,
		VersionedPrinter,
	}
	public static enum FontType implements SubType {
		Raster,
		TrueType,
		Vector,
	}
	
}
