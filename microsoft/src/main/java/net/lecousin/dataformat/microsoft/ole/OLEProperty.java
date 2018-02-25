package net.lecousin.dataformat.microsoft.ole;

import java.text.SimpleDateFormat;

import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.locale.LocalizableString;
import net.lecousin.framework.locale.ToLocalizableString;

public interface OLEProperty {

	public static interface IDS {
		public static final long CODEPAGE = 0x00000001;
	}
	
	public static interface CodePages {
		
	    /** <p>Codepage 037, a special case</p> */
	    public static final int CP_037 = 37;

	    /** <p>Codepage for SJIS</p> */
	    public static final int CP_SJIS = 932;

	    /** <p>Codepage for GBK, aka MS936</p> */
	    public static final int CP_GBK = 936;

	    /** <p>Codepage for MS949</p> */
	    public static final int CP_MS949 = 949;

	    /** <p>Codepage for UTF-16</p> */
	    public static final int CP_UTF16 = 1200;

	    /** <p>Codepage for UTF-16 big-endian</p> */
	    public static final int CP_UTF16_BE = 1201;

	    /** <p>Codepage for Windows 1250</p> */
	    public static final int CP_WINDOWS_1250 = 1250;

	    /** <p>Codepage for Windows 1251</p> */
	    public static final int CP_WINDOWS_1251 = 1251;

	    /** <p>Codepage for Windows 1252</p> */
	    public static final int CP_WINDOWS_1252 = 1252;

	    /** <p>Codepage for Windows 1253</p> */
	    public static final int CP_WINDOWS_1253 = 1253;

	    /** <p>Codepage for Windows 1254</p> */
	    public static final int CP_WINDOWS_1254 = 1254;

	    /** <p>Codepage for Windows 1255</p> */
	    public static final int CP_WINDOWS_1255 = 1255;

	    /** <p>Codepage for Windows 1256</p> */
	    public static final int CP_WINDOWS_1256 = 1256;

	    /** <p>Codepage for Windows 1257</p> */
	    public static final int CP_WINDOWS_1257 = 1257;

	    /** <p>Codepage for Windows 1258</p> */
	    public static final int CP_WINDOWS_1258 = 1258;

	    /** <p>Codepage for Johab</p> */
	    public static final int CP_JOHAB = 1361;

	    /** <p>Codepage for Macintosh Roman (Java: MacRoman)</p> */
	    public static final int CP_MAC_ROMAN = 10000;

	    /** <p>Codepage for Macintosh Japan (Java: unknown - use SJIS, cp942 or
	     * cp943)</p> */
	    public static final int CP_MAC_JAPAN = 10001;

	    /** <p>Codepage for Macintosh Chinese Traditional (Java: unknown - use Big5,
	     * MS950, or cp937)</p> */
	    public static final int CP_MAC_CHINESE_TRADITIONAL = 10002;

	    /** <p>Codepage for Macintosh Korean (Java: unknown - use EUC_KR or
	     * cp949)</p> */
	    public static final int CP_MAC_KOREAN = 10003;

	    /** <p>Codepage for Macintosh Arabic (Java: MacArabic)</p> */
	    public static final int CP_MAC_ARABIC = 10004;

	    /** <p>Codepage for Macintosh Hebrew (Java: MacHebrew)</p> */
	    public static final int CP_MAC_HEBREW = 10005;

	    /** <p>Codepage for Macintosh Greek (Java: MacGreek)</p> */
	    public static final int CP_MAC_GREEK = 10006;

	    /** <p>Codepage for Macintosh Cyrillic (Java: MacCyrillic)</p> */
	    public static final int CP_MAC_CYRILLIC = 10007;

	    /** <p>Codepage for Macintosh Chinese Simplified (Java: unknown - use
	     * EUC_CN, ISO2022_CN_GB, MS936 or cp935)</p> */
	    public static final int CP_MAC_CHINESE_SIMPLE = 10008;

	    /** <p>Codepage for Macintosh Romanian (Java: MacRomania)</p> */
	    public static final int CP_MAC_ROMANIA = 10010;

	    /** <p>Codepage for Macintosh Ukrainian (Java: MacUkraine)</p> */
	    public static final int CP_MAC_UKRAINE = 10017;

	    /** <p>Codepage for Macintosh Thai (Java: MacThai)</p> */
	    public static final int CP_MAC_THAI = 10021;

	    /** <p>Codepage for Macintosh Central Europe (Latin-2)
	     * (Java: MacCentralEurope)</p> */
	    public static final int CP_MAC_CENTRAL_EUROPE = 10029;

	    /** <p>Codepage for Macintosh Iceland (Java: MacIceland)</p> */
	    public static final int CP_MAC_ICELAND = 10079;

	    /** <p>Codepage for Macintosh Turkish (Java: MacTurkish)</p> */
	    public static final int CP_MAC_TURKISH = 10081;

	    /** <p>Codepage for Macintosh Croatian (Java: MacCroatian)</p> */
	    public static final int CP_MAC_CROATIAN = 10082;

	    /** <p>Codepage for US-ASCII</p> */
	    public static final int CP_US_ACSII = 20127;

	    /** <p>Codepage for KOI8-R</p> */
	    public static final int CP_KOI8_R = 20866;

	    /** <p>Codepage for ISO-8859-1</p> */
	    public static final int CP_ISO_8859_1 = 28591;

	    /** <p>Codepage for ISO-8859-2</p> */
	    public static final int CP_ISO_8859_2 = 28592;

	    /** <p>Codepage for ISO-8859-3</p> */
	    public static final int CP_ISO_8859_3 = 28593;

	    /** <p>Codepage for ISO-8859-4</p> */
	    public static final int CP_ISO_8859_4 = 28594;

	    /** <p>Codepage for ISO-8859-5</p> */
	    public static final int CP_ISO_8859_5 = 28595;

	    /** <p>Codepage for ISO-8859-6</p> */
	    public static final int CP_ISO_8859_6 = 28596;

	    /** <p>Codepage for ISO-8859-7</p> */
	    public static final int CP_ISO_8859_7 = 28597;

	    /** <p>Codepage for ISO-8859-8</p> */
	    public static final int CP_ISO_8859_8 = 28598;

	    /** <p>Codepage for ISO-8859-9</p> */
	    public static final int CP_ISO_8859_9 = 28599;

	    /** <p>Codepage for ISO-2022-JP</p> */
	    public static final int CP_ISO_2022_JP1 = 50220;

	    /** <p>Another codepage for ISO-2022-JP</p> */
	    public static final int CP_ISO_2022_JP2 = 50221;

	    /** <p>Yet another codepage for ISO-2022-JP</p> */
	    public static final int CP_ISO_2022_JP3 = 50222;

	    /** <p>Codepage for ISO-2022-KR</p> */
	    public static final int CP_ISO_2022_KR = 50225;

	    /** <p>Codepage for EUC-JP</p> */
	    public static final int CP_EUC_JP = 51932;

	    /** <p>Codepage for EUC-KR</p> */
	    public static final int CP_EUC_KR = 51949;

	    /** <p>Codepage for GB2312</p> */
	    public static final int CP_GB2312 = 52936;

	    /** <p>Codepage for GB18030</p> */
	    public static final int CP_GB18030 = 54936;

	    /** <p>Another codepage for US-ASCII</p> */
	    public static final int CP_US_ASCII2 = 65000;

	    /** <p>Codepage for UTF-8</p> */
	    public static final int CP_UTF8 = 65001;

	    /** <p>Codepage for Unicode</p> */
	    public static final int CP_UNICODE = CP_UTF16;

		public static String codepageToEncoding( final int codepage ) {
	        if ( codepage <= 0 ) return null;
	        switch ( codepage )
	        {
	        case CP_UTF16:
	            return "UTF-16";
	        case CP_UTF16_BE:
	            return "UTF-16BE";
	        case CP_UTF8:
	            return "UTF-8";
	        case CP_037:
	            return "cp037";
	        case CP_GBK:
	            return "GBK";
	        case CP_MS949:
	            return "ms949";
	        case CP_WINDOWS_1250:
	            return "windows-1250";
	        case CP_WINDOWS_1251:
	            return "windows-1251";
	        case CP_WINDOWS_1252:
	            return "windows-1252";
	        case CP_WINDOWS_1253:
	            return "windows-1253";
	        case CP_WINDOWS_1254:
	            return "windows-1254";
	        case CP_WINDOWS_1255:
	            return "windows-1255";
	        case CP_WINDOWS_1256:
	            return "windows-1256";
	        case CP_WINDOWS_1257:
	            return "windows-1257";
	        case CP_WINDOWS_1258:
	            return "windows-1258";
	        case CP_JOHAB:
	            return "johab";
	        case CP_MAC_ROMAN:
	            return "MacRoman";
	        case CP_MAC_JAPAN:
	            return "SJIS";
	        case CP_MAC_CHINESE_TRADITIONAL:
	            return "Big5";
	        case CP_MAC_KOREAN:
	            return "EUC-KR";
	        case CP_MAC_ARABIC:
	            return "MacArabic";
	        case CP_MAC_HEBREW:
	            return "MacHebrew";
	        case CP_MAC_GREEK:
	            return "MacGreek";
	        case CP_MAC_CYRILLIC:
	            return "MacCyrillic";
	        case CP_MAC_CHINESE_SIMPLE:
	            return "EUC_CN";
	        case CP_MAC_ROMANIA:
	            return "MacRomania";
	        case CP_MAC_UKRAINE:
	            return "MacUkraine";
	        case CP_MAC_THAI:
	            return "MacThai";
	        case CP_MAC_CENTRAL_EUROPE:
	            return "MacCentralEurope";
	        case CP_MAC_ICELAND:
	            return "MacIceland";
	        case CP_MAC_TURKISH:
	            return "MacTurkish";
	        case CP_MAC_CROATIAN:
	            return "MacCroatian";
	        case CP_US_ACSII:
	        case CP_US_ASCII2:
	            return "US-ASCII";
	        case CP_KOI8_R:
	            return "KOI8-R";
	        case CP_ISO_8859_1:
	            return "ISO-8859-1";
	        case CP_ISO_8859_2:
	            return "ISO-8859-2";
	        case CP_ISO_8859_3:
	            return "ISO-8859-3";
	        case CP_ISO_8859_4:
	            return "ISO-8859-4";
	        case CP_ISO_8859_5:
	            return "ISO-8859-5";
	        case CP_ISO_8859_6:
	            return "ISO-8859-6";
	        case CP_ISO_8859_7:
	            return "ISO-8859-7";
	        case CP_ISO_8859_8:
	            return "ISO-8859-8";
	        case CP_ISO_8859_9:
	            return "ISO-8859-9";
	        case CP_ISO_2022_JP1:
	        case CP_ISO_2022_JP2:
	        case CP_ISO_2022_JP3:
	            return "ISO-2022-JP";
	        case CP_ISO_2022_KR:
	            return "ISO-2022-KR";
	        case CP_EUC_JP:
	            return "EUC-JP";
	        case CP_EUC_KR:
	            return "EUC-KR";
	        case CP_GB2312:
	            return "GB2312";
	        case CP_GB18030:
	            return "GB18030";
	        case CP_SJIS:
	            return "SJIS";
	        default:
	            return "cp" + codepage;
	        }
	    }
	}
	
	public static class Empty implements OLEProperty {
		@Override
		public String toString() { return ""; }
	}
	public static class Null implements OLEProperty {
		@Override
		public String toString() { return ""; }
	}
	
	public static class Bool implements OLEProperty, ToLocalizableString {
		public Bool(boolean value) { this.value = value; }
		public boolean value;
		@Override
		public ILocalizableString toLocalizable() { return new LocalizableString("b", "true"); }
	}
	public static class Byte implements OLEProperty {
		public Byte(byte value) { this.value = value; }
		public byte value;
		@Override
		public String toString() { return java.lang.Byte.toString(value); }
	}
	public static class Short implements OLEProperty {
		public Short(short value) { this.value = value; }
		public short value;
		@Override
		public String toString() { return java.lang.Short.toString(value); }
	}
	public static class Int implements OLEProperty {
		public Int(int value) { this.value = value; }
		public int value;
		@Override
		public String toString() { return java.lang.Integer.toString(value); }
	}
	public static class Long implements OLEProperty {
		public Long(long value) { this.value = value; }
		public long value;
		@Override
		public String toString() { return java.lang.Long.toString(value); }
	}
	public static class Float implements OLEProperty {
		public Float(float value) { this.value = value; }
		public float value;
		@Override
		public String toString() { return java.lang.Float.toString(value); }
	}
	public static class Double implements OLEProperty {
		public Double(double value) { this.value = value; }
		public double value;
		@Override
		public String toString() { return java.lang.Double.toString(value); }
	}

	public static class UnsignedByte implements OLEProperty {
		public UnsignedByte(short value) { this.value = value; }
		public short value;
		@Override
		public String toString() { return java.lang.Short.toString(value); }
	}
	public static class UnsignedShort implements OLEProperty {
		public UnsignedShort(int value) { this.value = value; }
		public int value;
		@Override
		public String toString() { return java.lang.Integer.toString(value); }
	}
	public static class UnsignedInt implements OLEProperty {
		public UnsignedInt(long value) { this.value = value; }
		public long value;
		@Override
		public String toString() { return java.lang.Long.toString(value); }
	}
	public static class UnsignedLong implements OLEProperty {
		public UnsignedLong(long value) { this.value = value; }
		public long value;
		@Override
		public String toString() { return java.lang.Long.toString(value); }
	}
	
	public static class Str implements OLEProperty {
		public Str(String value) { this.value = value; }
		public String value;
		@Override
		public String toString() { return value; }
	}
	
	public static class Currency extends Double {
		public Currency(double value) { super(value); }
	}
	public static class Date extends Long {
		public Date(long value) { super(value); }
		@Override
		public String toString() { return new SimpleDateFormat("dd-MM-yyyy HH:mm").format(new java.util.Date(value)); }
	}
	public static class CodePageString extends Str {
		public CodePageString(String value) { super(value); }
	}
	public static class Error extends UnsignedInt {
		public Error(long value) { super(value); }
	}
	
	public static class Bytes implements OLEProperty {
		public Bytes(long offset, long size) { this.offset = offset; this.size = size; }
		public long offset;
		public long size;
	}
	
	public static final long EPOCH_DIFF = 11644473600000L;
	
}
