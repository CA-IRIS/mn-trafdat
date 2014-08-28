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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet for serving IRIS traffic sample data.
 *
 * @author john3tim
 * @author Douglas Lau
 */
public class TrafdatServlet extends HttpServlet {

	/** Maximum length of a data filename */
	static private final int MAX_FILENAME_LENGTH = 24;

	/** Traffic file extension */
	static private final String EXT = ".traffic";

	/** Path to directory containing traffic data files */
	static private final String BASE_PATH = "/var/lib/iris/traffic";

	/** Default district ID */
	static private final String DEFAULT_DISTRICT = "tms";

	/** Get the file path to the given archive path.
	 * @param district District ID.
	 * @param path Archive relative path to file.
	 * @return Path to directory in sample archive. */
	static private File getFilePath(String district, String path) {
		return new File(BASE_PATH + File.separator + district +
			File.separator + path);
	}

	/** Get the file path to the given date.
	 * @param district District ID.
	 * @param date String date (8 digits yyyyMMdd).
	 * @return Path to file in sample archive. */
	static private File getDatePath(String district, String date) {
		String year = date.substring(0, 4);
		return getFilePath(district, year + File.separator + date);
	}

	/** Get the file path to the given date traffic file.
	 * @param district District ID.
	 * @param date String date (8 digits yyyyMMdd).
	 * @return Path to file in sample archive. */
	static private File getTrafficPath(String district, String date) {
		String year = date.substring(0, 4);
		return getFilePath(district, year + File.separator + date +EXT);
	}

	/** Split a request path into component parts.
	 * @param p Request path
	 * @return Array of path components. */
	static private String[] splitRequestPath(String pathInfo) {
		String[] p = splitPath(pathInfo);
		// Backward compatibility stuff:
		//    check if district path was omitted
		if(p.length > 0 && isValidYear(p[0])) {
			if(pathInfo.startsWith("/"))
				return splitPath(DEFAULT_DISTRICT + pathInfo);
			else
				return splitPath(DEFAULT_DISTRICT+"/"+pathInfo);
		} else
			return p;
	}

	/** Split a path into component parts.
	 * @param p Path
	 * @return Array of path components. */
	static private String[] splitPath(String p) {
		if(p != null) {
			while(p.startsWith("/"))
				p = p.substring(1);
			return p.split("/");
		}
		return new String[0];
	}

	/** Check if the given year is valid.
	 * @param year String year (4 digits, yyyy).
	 * @return true if year is valid, otherwise false */
	static private boolean isValidYear(String year) {
		try {
			Integer.parseInt(year);
			return year.length() == 4;
		}
		catch(NumberFormatException e) {
			return false;
		}
	}

	/** Check if the given date is valid.
	 * @param date String date (8 digits yyyyMMdd)
	 * @return true if date is valid, otherwise false */
	static private boolean isValidDate(String date) {
		try {
			Integer.parseInt(date);
			return date.length() == 8;
		}
		catch(NumberFormatException e) {
			return false;
		}
	}

	/** Check if the given year and date is valid.
	 * @param year String year (4 digits, yyyy).
	 * @param date String date (8 digits yyyyMMdd)
	 * @return true if date is valid, otherwise false */
	static private boolean isValidYearDate(String year, String date) {
		return isValidYear(year) &&
		       isValidDate(date) &&
		       date.startsWith(year);
	}

	/** Check if the given file name is valid.
	 * @param name Name of sample file.
	 * @return true if name is valid, otherwise false */
	static private boolean isFileNameValid(String name) {
		return name.length() <= MAX_FILENAME_LENGTH;
	}

	/** Check if the given file name is a JSON file.
	 * @param name Name of sample file.
	 * @return true if name is valid, otherwise false */
	static private boolean isJsonFile(String name) {
		return name.endsWith(".json");
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

	/** Get a sample reader for the specified sample file name.
	 * @param name Name of sample file.
	 * @return SampleReader to read samples from the file. */
	static private SampleReader getSampleReader(String name) {
		if(name.endsWith(".c30") || name.endsWith(".pr60"))
			return new ShortSampleReader();
		else
			return new ByteSampleReader();
	}

	/** Initialize the servlet */
	@Override
	public void init(ServletConfig config) throws ServletException {
		// Nothing to initialize
	}

	/** Process an HTTP GET request */
	@Override
	public void doGet(HttpServletRequest request,
		HttpServletResponse response)
	{
		String pathInfo = request.getPathInfo();
		try {
			if(!processRequest(pathInfo, response)) {
				response.sendError(
					HttpServletResponse.SC_BAD_REQUEST);
			}
		}
		catch(IOException e) {
			e.printStackTrace();
			try {
				response.sendError(HttpServletResponse.
					SC_INTERNAL_SERVER_ERROR);
			}
			catch(IOException ee) {
				ee.printStackTrace();
			}
		}
	}

	/** Process a traffic data request from a client.
	 * @param pathInfo Path of requested resource.
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	private boolean processRequest(String pathInfo,
		HttpServletResponse response) throws IOException
	{
		String[] p = splitRequestPath(pathInfo);
		switch(p.length) {
		case 2:
			return processDateRequest(p[0], p[1], response);
		case 3:
			return processSensorRequest(p[0], p[1], p[2], response);
		case 4:
			return processSampleRequest(p[0], p[1], p[2], p[3],
				response);
		default:
			return false;
		}
	}

	/** Process a request for the available dates for a given year.
	 * @param district District ID.
	 * @param year String year (4 digits, yyyy).
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	private boolean processDateRequest(String district, String year,
		HttpServletResponse response) throws IOException
	{
		if(isValidYear(year)) {
			File f = getFilePath(district, year);
			if(f.canRead() && f.isDirectory())
				writeDates(f, response);
			return true;
		} else
			return false;
	}

	/** Write out the dates available for the given directory.
	 * @param path Path to year archive.
	 * @param response Servlet response. */
	private void writeDates(File path, HttpServletResponse response)
		throws IOException
	{
		Writer w = createWriter(response);
		try {
			for(String name: path.list()) {
				String date = getTrafficDate(path, name);
				if(date != null)
					w.write(date + "\n");
			}
			w.flush();
		}
		finally {
			w.close();
		}
	}

	/** Create a buffered writer for the response.
	 * @param response Servlet response.
	 * @return Buffered writer for the response. */
	static private Writer createWriter(HttpServletResponse response)
		throws IOException
	{
		OutputStream os = response.getOutputStream();
		OutputStreamWriter osw = new OutputStreamWriter(os);
		return new BufferedWriter(osw);
	}

	/** Get the date string for the given file.
	 * @param path Path to year archive.
	 * @param name Name of file in archive.
	 * @return Date represented by file, or null */
	static private String getTrafficDate(File path, String name) {
		if(name.length() < 8)
			return null;
		String date = name.substring(0, 8);
		if(!isValidDate(date))
			return null;
		File file = new File(path, name);
		if(!file.canRead())
			return null;
		if(name.length() == 8 && file.isDirectory())
			return date;
		if(name.length() == 16 && name.endsWith(EXT))
			return date;
		return null;
	}

	/** Process a sensor list request.
	 * @param district District ID.
	 * @param year String year (4 digits, yyyy).
	 * @param date String date (8 digits yyyyMMdd).
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	private boolean processSensorRequest(String district, String year,
		String date, HttpServletResponse response) throws IOException
	{
		if(isValidYearDate(year, date)) {
			Set<String> sensors = lookupSensors(district, date);
			response.setContentType("application/json");
			Writer w = createWriter(response);
			try {
				w.write('[');
				boolean first = true;
				for(String s: sensors) {
					if(!first)
						w.write(',');
					w.write('"' + s + '"');
					first = false;
				}
				w.write(']');
				w.flush();
			}
			finally {
				w.close();
			}
			return true;
		} else
			return false;
	}

	/** Lookup the sensors available for the given date.
	 * @param district District ID.
	 * @param date String date (8 digits yyyyMMdd).
	 * @return A set of sensor IDs available for the date. */
	private Set<String> lookupSensors(String district, String date)
		throws IOException
	{
		TreeSet<String> sensors = new TreeSet<String>();
		File traffic = getTrafficPath(district, date);
		if(traffic.canRead() && traffic.isFile()) {
			ZipFile zf = new ZipFile(traffic);
			Enumeration e = zf.entries();
			while(e.hasMoreElements()) {
				ZipEntry ze = (ZipEntry)e.nextElement();
				String name = ze.getName();
				if(isValidSampleFile(name))
					sensors.add(getSensorId(name));
			}
		}
		File dir = getDatePath(district, date);
		if(dir.canRead() && dir.isDirectory()) {
			for(String name: dir.list()) {
				if(isValidSampleFile(name))
					sensors.add(getSensorId(name));
			}
		}
		return sensors;
	}

	/** Get the sensor ID for a given file name.
	 * @param name Sample file name.
	 * @return Sensor ID. */
	static private String getSensorId(String name) {
		int i = name.indexOf('.');
		if(i > 0)
			return name.substring(0, i);
		else
			return name;
	}

	/** Process a sample data request.
	 * @param district District ID.
	 * @param year String year (4 digits, yyyy).
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	private boolean processSampleRequest(String district, String year,
		String date, String name, HttpServletResponse response)
		throws IOException
	{
		if(isValidYearDate(year, date) && isFileNameValid(name)) {
			try {
				return processSampleRequest(district, date,
					name, response);
			}
			catch(FileNotFoundException e) {
				response.sendError(
					HttpServletResponse.SC_NOT_FOUND);
				return true;
			}
		} else
			return false;
	}

	/** Process a sample data request.
	 * @param district District ID.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	private boolean processSampleRequest(String district, String date,
		String name, HttpServletResponse response) throws IOException
	{
		if(isJsonFile(name))
			return processJsonRequest(district, date,name,response);
		else if(isValidSampleFile(name)) {
			InputStream in = getTrafficInputStream(district, date,
				name);
			try {
				sendData(in, response);
				return true;
			}
			finally {
				in.close();
			}
		} else
			return false;
	}

	/** Process a JSON data request.
	 * @param district District ID.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	private boolean processJsonRequest(String district, String date,
		String name, HttpServletResponse response) throws IOException
	{
		name = name.substring(0, name.length() - 5);
		if(isBinnedFile(name)) {
			InputStream in = getTrafficInputStream(district, date,
				name);
			try {
				sendJsonData(in, response,
					getSampleReader(name));
				return true;
			}
			finally {
				in.close();
			}
		} else
			return false;
	}

	/** Get an InputStream for the given date.
	 * @param district District ID.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @return InputStream from which sample data can be read. */
	static private InputStream getTrafficInputStream(String district,
		String date, String name) throws IOException
	{
		try {
			return getZipInputStream(district, date, name);
		}
		catch(FileNotFoundException e) {
			try {
				return getFileInputStream(district, date, name);
			}
			catch(FileNotFoundException ee) {
				return getBinnedVLogInputStream(district, date,
					name);
			}
		}
	}

	/** Get a sample InputStream from a zip (traffic) file.
	 * @param district District ID.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Name of sample file within .traffic file.
	 * @return InputStream from which sample data can be read. */
	static private InputStream getZipInputStream(String district,
		String date, String name) throws IOException
	{
		File traffic = getTrafficPath(district, date);
		try {
			ZipFile zip = new ZipFile(traffic);
			ZipEntry entry = zip.getEntry(name);
			if(entry != null)
				return zip.getInputStream(entry);
		}
		catch(ZipException e) {
			// Defer to FileNotFoundException, below
		}
		throw new FileNotFoundException(name);
	}

	/** Get a sample input stream from a regular file.
	 * @param district District ID.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @return InputStream from which sample data can be read. */
	static private InputStream getFileInputStream(String district,
		String date, String name) throws IOException
	{
		return new FileInputStream(new File(getDatePath(district, date),
			name));
	}

	/** Get a sample input stream by binning a .vlog file.
	 * @param district District ID.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @return InputStream from which sample data can be read. */
	static private InputStream getBinnedVLogInputStream(String district,
		String date, String name) throws IOException
	{
		SampleBin bin = createSampleBin(name);
		if(bin != null) {
			String n = getVLogName(name);
			VehicleEventLog log = createVLog(
				getTrafficInputStream(district, date, n));
			log.bin30SecondSamples(bin);
			return new ByteArrayInputStream(bin.getData());
		} else
			throw new FileNotFoundException(name);
	}

	/** Create a sample bin for the given file name.
	 * @param name Name of sample file.
	 * @return Sample bin for specified file. */
	static private SampleBin createSampleBin(String name) {
		if(name.endsWith(".v30"))
			return new VolumeSampleBin();
		else if(name.endsWith(".s30"))
			return new SpeedSampleBin();
		else
			return null;
	}

	/** Create and process a vehicle event log.
	 * @param in InputStream to read .vlog events.
	 * @return Vehicle event log object. */
	static private VehicleEventLog createVLog(InputStream in)
		throws IOException
	{
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

	/** Get the file name with .vlog extension.
	 * @param name Sample file name.
	 * @return Name of corresponding .vlog sample file. */
	static private String getVLogName(String name) {
		return name.substring(0, name.length() - 3) + "vlog";
	}

	/** Send data from the given input stream to the response.
	 * @param in Input stream to read data from.
	 * @param response Servlet response object. */
	static private void sendData(InputStream in,
		HttpServletResponse response) throws IOException
	{
		byte[] buf = new byte[4096];
		response.setContentType("application/octet-stream");
		OutputStream out = response.getOutputStream();
		try {
			while(true) {
				int n_bytes = in.read(buf);
				if(n_bytes < 0)
					break;
				out.write(buf, 0, n_bytes);
			}
		}
		finally {
			out.close();
		}
	}

	/** Interface for sample data readers */
	static private interface SampleReader {
		int getSample(DataInputStream dis) throws IOException;
	}

	/** Class to read byte samples from a data input stream */
	static private class ByteSampleReader implements SampleReader {
		public int getSample(DataInputStream dis) throws IOException {
			return dis.readByte();
		}
	}

	/** Class to read short samples from a data input stream */
	static private class ShortSampleReader implements SampleReader {
		public int getSample(DataInputStream dis) throws IOException {
			return dis.readShort();
		}
	}

	/** Send data from the given input stream to the response as JSON.
	 * @param in Input stream to read data from.
	 * @param response Servlet response object. */
	static private void sendJsonData(InputStream in,
		HttpServletResponse response, SampleReader sr)
		throws IOException
	{
		BufferedInputStream bis = new BufferedInputStream(in);
		DataInputStream dis = new DataInputStream(bis);
		response.setContentType("application/json");
		Writer w = createWriter(response);
		try {
			w.write('[');
			boolean first = true;
			while(true) {
				try {
					String sam = formatJson(
						sr.getSample(dis));
					if(!first)
						w.write(',');
					w.write(sam);
					first = false;
				}
				catch(EOFException e) {
					break;
				}
			}
			w.write(']');
			w.flush();
		}
		finally {
			w.close();
		}
	}

	/** Format a number as a JSON value.
	 * @param val Number to format.
	 * @return JSON value. */
	static private String formatJson(int val) {
		if(val >= 0)
			return Integer.toString(val);
		else
			return "null";
	}
}
