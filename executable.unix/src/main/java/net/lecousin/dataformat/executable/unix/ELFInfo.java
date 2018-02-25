package net.lecousin.dataformat.executable.unix;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.ui_description.annotations.name.FixedName;

public class ELFInfo implements DataFormatInfo {

	public static final String PROPERTY_NAME = "ELFInfo";
	
	public static enum Bits {
		_32("32-bits"),
		_64("64-bits");
		
		private String name;
		private Bits(String name) {
			this.name = name;
		}
		@Override
		public String toString() { return name; }
	}

	@FixedName("Architecture")
	public Bits bits;
	
	public static enum Endianness {
		LE("Little-endian"),
		BE("Big-endian");
		
		private String name;
		private Endianness(String name) {
			this.name = name;
		}
		@Override
		public String toString() { return name; }
	}
	
	@FixedName("Endianness")
	public Endianness endianness;
	
	public static enum TargetOS {
		Unknown((byte)0xFF, "Unknown"),
		SystemV((byte)0, "System V"),
		HPUX((byte)1, "HP-UX"),
		NetBSD((byte)2, "NetBSD"),
		Linux((byte)3, "Linux"),
		Solaris((byte)6, "Solaris"),
		AIX((byte)7, "AIX"),
		IRIX((byte)8, "IRIX"),
		FreeBSD((byte)9, "FreeBSD"),
		OpenBSD((byte)0xC, "OpenBSD"),
		OpenVMS((byte)0xD, "OpenVMS"),
		NonStopKernel((byte)0xE, "NonStop Kernel"),
		AROS((byte)0xF, "AROS"),
		FenixOS((byte)0x10, "Fenix OS"),
		CloudABI((byte)0x11, "CloudABI"),
		Sortix((byte)0x53, "Sortix");

		private byte value;
		private String name;
		private TargetOS(byte value, String name) {
			this.value = value;
			this.name = name;
		}
		@Override
		public String toString() { return name; }
		public int getValue() { return value; }
		public String getName() { return name; }
		public static TargetOS get(byte value) {
			for (TargetOS m : values())
				if (m.value == value) return m;
			return Unknown;
		}
	}

	@FixedName("Target OS")
	public TargetOS targetOS;
	
	public static enum Type {
		RELOCATABLE("Relocatable"),
		EXECUTABLE("Executable"),
		SHARED("Shared library"),
		CORE("Core");

		private String name;
		private Type(String name) {
			this.name = name;
		}
		@Override
		public String toString() { return name; }
	}
	
	@FixedName("Type")
	public Type type;

	long programHeaderTableOffset;
	long sectionHeaderTableOffset;
	int programHeaderTableEntrySize;
	int programHeaderTableNbEntries;
	int sectionHeaderTableEntrySize;
	int sectionHeaderTableNbEntries;
	int sectionNamesIndex;
}
