package net.lecousin.dataformat.executable.windows.coff;

import net.lecousin.dataformat.core.DataFormatInfo;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;

public class COFFInfo implements DataFormatInfo {

	public static enum TargetMachine {
		Unknown(-1, "Unknown"),
		Any(0, "Any"),
		i386(0x14c, "Intel 386 or later"),
		i860(0x14d, "Intel i860"),
		MIPS_R3000(0x162, "MIPS R3000"),
		MIPS_R4000(0x166, "MIPS R4000"),
		MIPS_R10000(0x168, "MIPS R10000"),
		MIPS_WCEv2(0x169, "MIPS little endian WCE v2"),
		DEC_AXP_Old(0x183, "old DEC Alpha AXP"),
		DEC_AXP(0x184, "Alpha AXP"),
		Hitashi_SH3(0x1a2, "Hitashi SH3"),
		Hitashi_SH3_DSP(0x1a3, "Hitashi SH3 DSP"),
		Hitashi_SH4(0x1a6, "Hitashi SH4"),
		Hitashi_SH5(0x1a7, "Hitashi SH5"),
		Hitashi_SH5_b(0x1a8, "Hitashi SH5"),
		ARM_little_endian(0x1c0, "ARM little endian"),
		ARM(0x1c2, "ARM or Thumb (interworking)"),
		ARM_v7(0x1c4, "ARMv7 (or higher) Thumb mode only"),
		Matsushita_AM33(0x1D3, "Matsushita AM33"),
		PowerPC(0x1f0, "Power PC little endian"),
		PowerPC_FPU(0x1f1, "Power PC with FPU"),
		Itanium(0x200, "Intel Itanium processor family"),
		MIPS16(0x266, "MIPS16"),
		Motorola68000(0x268, "Motorola 68000 series"),
		DEC_AXP_64(0x284, "DEC Alpha AXP 64-bit"),
		MIPS_FPU(0x366, "MIPS with FPU"),
		MIPS16_FPU(0x466, "MIPS16 with FPU"),
		EFI(0xebc, "EFI byte code"),
		x64(0x8664, "x64"),
		Mitsubishi_M32R(0x9041, "Mitsubishi M32R little endian"),
		clr_pure_MSIL(0xc0ee, "clr pure MSIL")
		;

		private int value;
		private String name;
		private TargetMachine(int value, String name) {
			this.value = value;
			this.name = name;
		}
		@Override
		public String toString() { return name; }
		public int getValue() { return value; }
		public String getName() { return name; }
		public static TargetMachine get(int value) {
			for (TargetMachine m : values())
				if (m.value == value) return m;
			return Unknown;
		}
	}

	@LocalizedName(namespace="dataformat.executable.windows", key="Target Machine")
	public TargetMachine targetMachine;
	
}
