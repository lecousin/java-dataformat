package net.lecousin.dataformat.microsoft;

import java.lang.reflect.Field;

import net.lecousin.framework.locale.FixedLocalizedString;
import net.lecousin.framework.locale.ILocalizableString;
import net.lecousin.framework.ui_description.annotations.render.Renderer;

public class GUIDRenderer implements Renderer {

	@Override
	public ILocalizableString toDisplayString(Object instance, Field field) {
		try {
			Object guid = field.get(instance);
			return new FixedLocalizedString(GUID.GUIDToString((byte[])guid));
		} catch (Throwable t) {
			return new FixedLocalizedString("");
		}
	}
	
}
