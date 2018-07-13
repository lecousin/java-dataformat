package net.lecousin.dataformat.microsoft.ole;

import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.util.GUIDUtil;

public class DocumentSummaryInformation implements KnownOLEPropertySet {

	public static final byte[] FMTID = GUIDUtil.toGUID(0xD5CDD502L, 0x2E9C, 0x101B, 0x9397, 0x08002B2CF9AEL);
	
	public static final int CATEGORY = 0x00000002;
	public static final int PRES_FORMAT = 0x00000003;
	public static final int BYTE_COUNT = 0x00000004;
	public static final int LINE_COUNT = 0x00000005;
	public static final int PAR_COUNT = 0x00000006;
	public static final int SLIDE_COUNT = 0x00000007;
	public static final int NOTE_COUNT = 0x00000008;
	public static final int HIDDEN_COUNT = 0x00000009;
	public static final int MM_CLIP_COUNT = 0x0000000A;
	public static final int SCALE = 0x0000000B;
	public static final int HEADING_PAIR = 0x0000000C;
	public static final int DOC_PARTS = 0x0000000D;
	public static final int MANAGER = 0x0000000E;
	public static final int COMPANY = 0x0000000F;
	public static final int LINKS_DIRTY = 0x00000010;
	
	@Override
	public byte[] getFMTID() {
		return FMTID;
	}
	
	@Override
	public ILocalizableString getName(long prop_id) throws IgnoreIt {
		if (prop_id < 2) throw new IgnoreIt();
		if (prop_id > LINKS_DIRTY) return null;
		switch ((int)prop_id) {
		case CATEGORY: return new LocalizableString("dataformat.microsoft", "Category");
		case PRES_FORMAT: return new LocalizableString("dataformat.microsoft", "Format");
		case BYTE_COUNT: return new LocalizableString("dataformat.microsoft", "Byte count");
		case LINE_COUNT: return new LocalizableString("dataformat.microsoft", "Line count");
		case PAR_COUNT: return new LocalizableString("dataformat.microsoft", "Paragraph count");
		case SLIDE_COUNT: return new LocalizableString("dataformat.microsoft", "Slide count");
		case NOTE_COUNT: return new LocalizableString("dataformat.microsoft", "Note count");
		case HIDDEN_COUNT: return new LocalizableString("dataformat.microsoft", "Hidden count");
		case MM_CLIP_COUNT: return new LocalizableString("dataformat.microsoft", "Multimedia count");
		case SCALE: throw new IgnoreIt();
		case HEADING_PAIR: throw new IgnoreIt();
		case DOC_PARTS: return new LocalizableString("dataformat.microsoft", "Document parts");
		case MANAGER: return new LocalizableString("dataformat.microsoft", "Project manager");
		case COMPANY: return new LocalizableString("dataformat.microsoft", "Company");
		case LINKS_DIRTY: throw new IgnoreIt();
		}
		return null;
	}
	
}
