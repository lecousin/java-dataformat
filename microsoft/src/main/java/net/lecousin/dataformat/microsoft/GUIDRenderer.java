package net.lecousin.dataformat.microsoft;

import net.lecousin.framework.io.serialization.TypeDefinition;
import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.uidescription.annotations.render.Renderer;

public class GUIDRenderer implements Renderer {

	@Override
	public ILocalizableString toDisplayString(TypeDefinition type, Object value) {
		try {
			return new FixedLocalizedString(GUID.GUIDToString((byte[])value));
		} catch (Throwable t) {
			return new FixedLocalizedString("");
		}
	}
	
}
