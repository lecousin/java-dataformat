package net.lecousin.dataformat.microsoft.ole;

import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.util.GUIDUtil;

public class SummaryInformation implements KnownOLEPropertySet {

	public static final byte[] FMTID = GUIDUtil.ToGUID(0xF29F85E0L, 0x4FF9, 0x1068, 0xAB91, 0x08002B27B3D9L);
	
	public static final int TITLE = 0x00000002;
	public static final int SUBJECT = 0x00000003;
	public static final int AUTHOR = 0x00000004;
	public static final int KEYWORDS = 0x00000005;
	public static final int COMMENTS = 0x00000006;
	public static final int TEMPLATE = 0x00000007;
	public static final int LAST_AUTHOR = 0x00000008;
	public static final int REVISION_NUMBER = 0x00000009;
	public static final int EDIT_TIME = 0x0000000A;
	public static final int LAST_PRINTED = 0x0000000B;
	public static final int CREATE_TIME = 0x0000000C;
	public static final int LAST_SAVE_TIME = 0x0000000D;
	public static final int PAGE_COUNT = 0x0000000E;
	public static final int WORD_COUNT = 0x0000000F;
	public static final int CHAR_COUNT = 0x00000010;
	public static final int THUMBNAIL = 0x00000011;
	public static final int APPLICATION_NAME = 0x00000012;
	public static final int DOC_SECURITY = 0x00000013;
	
	@Override
	public byte[] getFMTID() {
		return FMTID;
	}
	
	@Override
	public ILocalizableString getName(long prop_id) {
		if (prop_id > DOC_SECURITY) return null;
		switch ((int)prop_id) {
		case TITLE: return new LocalizableString("dataformat.microsoft", "Title");
		case SUBJECT: return new LocalizableString("dataformat.microsoft", "Subject");
		case AUTHOR: return new LocalizableString("dataformat.microsoft", "Author");
		case KEYWORDS: return new LocalizableString("dataformat.microsoft", "Keywords");
		case COMMENTS: return new LocalizableString("dataformat.microsoft", "Comments");
		case TEMPLATE: return new LocalizableString("dataformat.microsoft", "Template");
		case LAST_AUTHOR: return new LocalizableString("dataformat.microsoft", "Last author");
		case REVISION_NUMBER: return new LocalizableString("dataformat.microsoft", "Revision number");
		case EDIT_TIME: return new LocalizableString("dataformat.microsoft", "Edit time");
		case LAST_PRINTED: return new LocalizableString("dataformat.microsoft", "Last printed");
		case CREATE_TIME: return new LocalizableString("dataformat.microsoft", "Create time");
		case LAST_SAVE_TIME: return new LocalizableString("dataformat.microsoft", "Last save time");
		case PAGE_COUNT: return new LocalizableString("dataformat.microsoft", "Page count");
		case WORD_COUNT: return new LocalizableString("dataformat.microsoft", "Word count");
		case CHAR_COUNT: return new LocalizableString("dataformat.microsoft", "Character count");
		case THUMBNAIL: return new LocalizableString("dataformat.microsoft", "Thumbnail");
		case APPLICATION_NAME: return new LocalizableString("dataformat.microsoft", "Application");
		case DOC_SECURITY: return new LocalizableString("dataformat.microsoft", "Security");
		}
		return null;
	}
	
}
