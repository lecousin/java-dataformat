package net.lecousin.dataformat.filesystem;

import net.lecousin.dataformat.core.DataCommonProperties;
import net.lecousin.framework.uidescription.annotations.name.LocalizedName;

public class PhysicalDriveDataCommonProperties extends DataCommonProperties {

	@LocalizedName(namespace="system.hardware",key="Manufacturer")
	public String manufacturer;
	
	@LocalizedName(namespace="system.hardware",key="Model")
	public String model;
	
	@LocalizedName(namespace="system.hardware",key="Version")
	public String version;
	
	@LocalizedName(namespace="system.hardware",key="Serial number")
	public String serialNumber;
	
}
