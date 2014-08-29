/*
 * Project: Trafdat
 * Copyright (C) 2007-2014  Minnesota Department of Transportation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package us.mn.state.dot.trafdat;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Sensor data archive.
 *
 * @author Douglas Lau
 */
public class TrafficArchive {

	/** Traffic file extension */
	static private final String EXT = ".traffic";

	/** Get the sensor ID for a given file name.
	 * @param name Sample file name.
	 * @return Sensor ID. */
	static private String sensor_id(String name) {
		int i = name.indexOf('.');
		return (i > 0) ? name.substring(0, i) : name;
	}

	/** Check if the given sample file name is valid.
	 * @param name Name of sample file.
	 * @return true if name is valid, otherwise false */
	static private boolean isValidSampleFile(String name) {
		return isBinnedFile(name) || name.endsWith(".vlog");
	}

	/** Check if the given file name is a binned sample file.
	 * @param name Name of sample file.
	 * @return true if name is for a binned sample file, otherwise false */
	static private boolean isBinnedFile(String name) {
		return name.endsWith(".v30") ||
		       name.endsWith(".c30") ||
		       name.endsWith(".s30") ||
		       name.endsWith(".pr60") ||
		       name.endsWith(".pt60");
	}

	/** Base sensor data path */
	private final File base_path;

	/** Get the file path to the given archive path.
	 * @param path Archive relative path to file.
	 * @return Path to directory in sample archive. */
	private File getFilePath(String path) {
		return new File(base_path, path);
	}

	/** Get the file path to the given date.
	 * @param date String date (8 digits yyyyMMdd).
	 * @return Path to file in sample archive. */
	private File getDatePath(String date) {
		assert date.length() == 8;
		String year = date.substring(0, 4);
		return new File(getFilePath(year), date);
	}

	/** Get the file path to the given date traffic file.
	 * @param date String date (8 digits yyyyMMdd).
	 * @return Path to file in sample archive. */
	private File getTrafficPath(String date) {
		assert date.length() == 8;
		String year = date.substring(0, 4);
		return new File(getFilePath(year), date + EXT);
	}

	/** Process a sensor list request.
	 * @param p Base path to sensor data.
	 * @param d District ID. */
	public TrafficArchive(String p, String d) {
		base_path = new File(p, d);
	}

	/** Lookup the sensors available for the given date.
	 * @param date String date (8 digits yyyyMMdd).
	 * @return A set of sensor IDs available for the date. */
	public Set<String> lookup(String date) throws IOException {
		assert date.length() == 8;
		TreeSet<String> sensors = new TreeSet<String>();
		File traffic = getTrafficPath(date);
		if (traffic.canRead() && traffic.isFile())
			lookup(traffic, sensors);
		File dir = getDatePath(date);
		if (dir.canRead() && dir.isDirectory()) {
			for (String name: dir.list()) {
				if (isValidSampleFile(name))
					sensors.add(sensor_id(name));
			}
		}
		return sensors;
	}

	/** Lookup all the sensors in a .traffic file.
	 * @param traffic Traffic file to lookup.
	 * @param sensors Sensor set.
	 * @throws IOException On file I/O error. */
	private void lookup(File traffic, TreeSet<String> sensors)
		throws IOException
	{
		ZipFile zf = new ZipFile(traffic);
		try {
			Enumeration e = zf.entries();
			while (e.hasMoreElements()) {
				ZipEntry ze = (ZipEntry)e.nextElement();
				String name = ze.getName();
				if (isValidSampleFile(name))
					sensors.add(sensor_id(name));
			}
		}
		finally {
			zf.close();
		}
	}
}
