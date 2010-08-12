/*
 * Project: Trafdat
 * Copyright (C) 2007-2010  Minnesota Department of Transportation
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
	static protected final int MAX_FILENAME_LENGTH = 24;

	/** Traffic file extension */
	static protected final String EXT = ".traffic";

	/** Path to directory containing traffic data files */
	static protected final String BASE_PATH = "/var/lib/iris/traffic";

	/** Get the file path to the given date.
	 * @param date String date (8 digits yyyyMMdd).
	 * @return Path to file in sample archive. */
	static protected String getDatePath(String date) {
		String year = date.substring(0, 4);
		return BASE_PATH + File.separator + year + File.separator +date;
	}

	/** Split a request path into component parts.
	 * @param p Request path
	 * @return Array of path components. */
	static private String[] splitRequestPath(String p) {
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
	static protected boolean isValidYear(String year) {
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
	static protected boolean isValidDate(String date) {
		try {
			Integer.parseInt(date);
			return date.length() == 8;
		}
		catch(NumberFormatException e) {
			return false;
		}
	}

	/** Check if the given file name is too long.
	 * @param name Name of sample file.
	 * @return true if name is too long, otherwise false */
	static protected boolean isFileNameTooLong(String name) {
		return name.length() > MAX_FILENAME_LENGTH;
	}

	/** Check if the given file name is a JSON file.
	 * @param name Name of sample file.
	 * @return true if name is valid, otherwise false */
	static protected boolean isJsonFile(String name) {
		return name.endsWith(".json");
	}

	/** Check if the given sample file name is valid.
	 * @param name Name of sample file.
	 * @return true if name is valid, otherwise false */
	static protected boolean isValidSampleFile(String name) {
		return isBinnedFile(name) || name.endsWith(".vlog");
	}

	/** Check if the given file name is a binned sample file.
	 * @param name Name of sample file.
	 * @return true if name is for a binned sample file, otherwise false */
	static protected boolean isBinnedFile(String name) {
		return name.endsWith(".v30") ||
		       name.endsWith(".c30") ||
		       name.endsWith(".s30") ||
		       name.endsWith(".pr60") ||
		       name.endsWith(".pt60");
	}

	/** Get a sample reader for the specified sample file name.
	 * @param name Name of sample file.
	 * @return SampleReader to read samples from the file. */
	static protected SampleReader getSampleReader(String name) {
		if(name.endsWith(".c30") || name.endsWith(".pr60"))
			return new ShortSampleReader();
		else
			return new ByteSampleReader();
	}

	/** Initialize the servlet */
	public void init(ServletConfig config) throws ServletException {
		// Nothing to initialize
	}

	/** Process an HTTP GET request */
	public void doGet(HttpServletRequest request,
		HttpServletResponse response)
	{
		String pathInfo = request.getPathInfo();
		try {
			if(!processRequest(pathInfo, response)) {
				response.setStatus(
					HttpServletResponse.SC_BAD_REQUEST);
			}
		}
		catch(IOException e) {
			e.printStackTrace();
			response.setStatus(
				HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		catch(VehicleEvent.Exception e) {
			e.printStackTrace();
			response.setStatus(
				HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/** Process a traffic data request from a client.
	 * @param pathInfo Path of requested resource.
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	protected boolean processRequest(String pathInfo,
		HttpServletResponse response) throws IOException,
		VehicleEvent.Exception
	{
		String[] p = splitRequestPath(pathInfo);
		switch(p.length) {
		case 1:
			return processDateRequest(p[0], response);
		case 2:
			return processFileRequest(p[0], p[1], response);
		case 3:
			return processSampleRequest(p[0], p[1], p[2], response);
		default:
			return false;
		}
	}

	/** Process a request for the available dates for a given year.
	 * @param year String year (4 digits, yyyy).
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	protected boolean processDateRequest(String year,
		HttpServletResponse response) throws IOException
	{
		if(!isValidYear(year))
			return false;
		Writer w = createWriter(response);
		try {
			writeDates(year, w);
			w.flush();
			return true;
		}
		finally {
			w.close();
		}
	}

	/** Create a buffered writer for the response.
	 * @param response Servlet response.
	 * @return Buffered writer for the response. */
	static protected Writer createWriter(HttpServletResponse response)
		throws IOException
	{
		OutputStream os = response.getOutputStream();
		OutputStreamWriter osw = new OutputStreamWriter(os);
		return new BufferedWriter(osw);
	}

	/** Write out the dates available for the given year.
	 * @param year String year (4 digits, yyyy).
	 * @param w Writer to output response. */
	protected void writeDates(String year, Writer w) throws IOException {
		File f = new File(BASE_PATH, year);
		if(f.canRead() && f.isDirectory())
			writeDates(f, w);
	}

	/** Write out the dates available for the given directory.
	 * @param path Path to year archive.
	 * @param w Writer to output response. */
	protected void writeDates(File path, Writer w) throws IOException {
		for(String name: path.list()) {
			String date = getTrafficDate(path, name);
			if(date != null)
				w.write(date + "\n");
		}
	}

	/** Get the date string for the given file.
	 * @param path Path to year archive.
	 * @param name Name of file in archive.
	 * @return Date represented by file, or null */
	static protected String getTrafficDate(File path, String name) {
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

	/** Process a sample data request.
	 * @param year String year (4 digits, yyyy).
	 * @param date String date (8 digits yyyyMMdd).
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	protected boolean processFileRequest(String year, String date,
		HttpServletResponse response) throws IOException
	{
		if(!isValidYear(year))
			return false;
		if(!isValidDate(date) || !date.startsWith(year))
			return false;
		Writer w = createWriter(response);
		try {
			writeFiles(date, w);
			w.flush();
			return true;
		}
		finally {
			w.close();
		}
	}

	/** Write out the samples files available for the given date.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param w Writer to output response. */
	protected void writeFiles(String date, Writer w) throws IOException {
		File traffic = new File(getDatePath(date) + EXT);
		if(traffic.canRead() && traffic.isFile()) {
			ZipFile zf = new ZipFile(traffic);
			Enumeration e = zf.entries();
			while(e.hasMoreElements()) {
				ZipEntry ze = (ZipEntry)e.nextElement();
				String name = ze.getName();
				if(isValidSampleFile(name))
					w.write(name + "\n");
			}
		}
		File dir = new File(getDatePath(date));
		if(dir.canRead() && dir.isDirectory()) {
			for(String name: dir.list()) {
				if(isValidSampleFile(name))
					w.write(name + "\n");
			}
		}
	}

	/** Process a sample data request.
	 * @param year String year (4 digits, yyyy).
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	protected boolean processSampleRequest(String year, String date,
		String name, HttpServletResponse response) throws IOException,
		VehicleEvent.Exception
	{
		if(!isValidYear(year))
			return false;
		if(!isValidDate(date) || !date.startsWith(year))
			return false;
		if(isFileNameTooLong(name))
			return false;
		try {
			if(isJsonFile(name))
				return processJsonRequest(date, name, response);
			else
				return processSampleRequest(date,name,response);
		}
		catch(FileNotFoundException e) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return true;
		}
	}

	/** Process a JSON data request.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	protected boolean processJsonRequest(String date, String name,
		HttpServletResponse response) throws IOException,
		VehicleEvent.Exception
	{
		name = name.substring(0, name.length() - 5);
		if(isBinnedFile(name)) {
			InputStream in = getTrafficInputStream(date, name);
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

	/** Process a sample data request.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	protected boolean processSampleRequest(String date, String name,
		HttpServletResponse response) throws IOException,
		VehicleEvent.Exception
	{
		if(isValidSampleFile(name)) {
			InputStream in = getTrafficInputStream(date, name);
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

	/** Get an InputStream for the given date.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @return InputStream from which sample data can be read. */
	static protected InputStream getTrafficInputStream(String date,
		String name) throws IOException, VehicleEvent.Exception
	{
		try {
			return getZipInputStream(date, name);
		}
		catch(FileNotFoundException e) {
			try {
				return getFileInputStream(date, name);
			}
			catch(FileNotFoundException ee) {
				return getBinnedVLogInputStream(date, name);
			}
		}
	}

	/** Get a sample InputStream from a zip (traffic) file.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Name of sample file within .traffic file.
	 * @return InputStream from which sample data can be read. */
	static protected InputStream getZipInputStream(String date, String name)
		throws IOException
	{
		String traffic = getDatePath(date) + EXT;
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
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @return InputStream from which sample data can be read. */
	static protected InputStream getFileInputStream(String date,
		String name) throws IOException
	{
		return new FileInputStream(getDatePath(date) + File.separator +
			name);
	}

	/** Get a sample input stream by binning a .vlog file.
	 * @param date String date (8 digits yyyyMMdd).
	 * @param name Sample file name.
	 * @return InputStream from which sample data can be read. */
	static protected InputStream getBinnedVLogInputStream(String date,
		String name) throws IOException, VehicleEvent.Exception
	{
		SampleBin bin = createSampleBin(name);
		if(bin != null) {
			String n = getVLogName(name);
			VehicleEventLog log = createVLog(
				getTrafficInputStream(date, n));
			log.bin30SecondSamples(bin);
			return new ByteArrayInputStream(bin.getData());
		} else
			throw new FileNotFoundException(name);
	}

	/** Create a sample bin for the given file name.
	 * @param name Name of sample file.
	 * @return Sample bin for specified file. */
	static protected SampleBin createSampleBin(String name) {
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
	static protected VehicleEventLog createVLog(InputStream in)
		throws IOException, VehicleEvent.Exception
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
	static protected String getVLogName(String name) {
		return name.substring(0, name.length() - 3) + "vlog";
	}

	/** Send data from the given input stream to the response.
	 * @param in Input stream to read data from.
	 * @param response Servlet response object. */
	static protected void sendData(InputStream in,
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
	static protected interface SampleReader {
		int getSample(DataInputStream dis) throws IOException;
	}

	/** Class to read byte samples from a data input stream */
	static protected class ByteSampleReader implements SampleReader {
		public int getSample(DataInputStream dis) throws IOException {
			return dis.readByte();
		}
	}

	/** Class to read short samples from a data input stream */
	static protected class ShortSampleReader implements SampleReader {
		public int getSample(DataInputStream dis) throws IOException {
			return dis.readShort();
		}
	}

	/** Send data from the given input stream to the response as JSON.
	 * @param in Input stream to read data from.
	 * @param response Servlet response object. */
	static protected void sendJsonData(InputStream in,
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
					if(first)
						w.write(sam);
					else
						w.write("," + sam);
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
	static protected String formatJson(int val) {
		if(val >= 0)
			return Integer.toString(val);
		else
			return "null";
	}
}
