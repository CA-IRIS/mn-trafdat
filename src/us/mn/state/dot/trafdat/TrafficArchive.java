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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

	/** Create a sample bin for the given file name.
	 * @param name Name of sample file.
	 * @return Sample bin for specified file. */
	static private SampleBin createSampleBin(String name) {
		if (name.endsWith(".v30"))
			return new VolumeSampleBin();
		else if (name.endsWith(".s30"))
			return new SpeedSampleBin();
		else
			return null;
	}

	/** Get the file name with .vlog extension.
	 * @param name Sample file name.
	 * @return Name of corresponding .vlog sample file. */
	static private String getVLogName(String name) {
		return name.substring(0, name.length() - 3) + "vlog";
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

	/** Get an InputStream for the given date and sample file.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @return InputStream from which sample data can be read. */
	public InputStream sampleInputStream(String date, String name)
		throws IOException
	{
		assert date.length() == 8;
		try {
			return getZipInputStream(date, name);
		}
		catch (FileNotFoundException e) {
			try {
				return getFileInputStream(date, name);
			}
			catch (FileNotFoundException ee) {
				return getBinnedVLogInputStream(date, name);
			}
		}
	}

	/** Get a sample InputStream from a zip (traffic) file.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Name of sample file within .traffic file.
	 * @return InputStream from which sample data can be read. */
	private InputStream getZipInputStream(String date, String name)
		throws IOException
	{
		File traffic = getTrafficPath(date);
		try {
			ZipFile zip = new ZipFile(traffic);
			ZipEntry entry = zip.getEntry(name);
			if (entry != null)
				return zip.getInputStream(entry);
		}
		catch (ZipException e) {
			// Defer to FileNotFoundException, below
		}
		throw new FileNotFoundException(name);
	}

	/** Get a sample input stream from a regular file.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @return InputStream from which sample data can be read. */
	private InputStream getFileInputStream(String date, String name)
		throws IOException
	{
		return new FileInputStream(new File(getDatePath(date), name));
	}

	/** Get a sample input stream by binning a .vlog file.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @return InputStream from which sample data can be read. */
	private InputStream getBinnedVLogInputStream(String date, String name)
		throws IOException
	{
		assert date.length() == 8;
		SampleBin bin = createSampleBin(name);
		if (bin != null) {
			String n = getVLogName(name);
			VehicleEventLog log = createVLog(
				sampleInputStream(date, n));
			log.bin30SecondSamples(bin);
			return new ByteArrayInputStream(bin.getData());
		} else
			throw new FileNotFoundException(name);
	}

	/** Create and process a vehicle event log.
	 * @param in InputStream to read .vlog events.
	 * @return Vehicle event log object. */
	private VehicleEventLog createVLog(InputStream in) throws IOException {
		try {
			InputStreamReader reader = new InputStreamReader(in);
			BufferedReader b = new BufferedReader(reader);
			VehicleEventLog log = new VehicleEventLog(b);
			log.propogateStampsForward();
			log.propogateStampsBackward();
			log.interpolateMissingStamps();
			return log;
		}
		finally {
			in.close();
		}
	}
}
