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
package us.mn.state.dot.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author john3tim
 * @author Douglas Lau
 */
public class DataServer extends HttpServlet {

	/** Maximum length of a data filename */
	static protected final int MAX_FILENAME_LENGTH = 20;

	/** Traffic file extension */
	static protected final String EXT = ".traffic";

	/** Path to directory containing traffic data files */
	static protected final String BASE_PATH = "/data/traffic";

	/** Split a request path into component parts */
	static private String[] splitRequestPath(String p) {
		if(p != null) {
			while(p.startsWith("/"))
				p = p.substring(1);
			return p.split("/");
		}
		return new String[0];
	}

	/** Check if the given year is valid */
	static protected boolean isValidYear(String year) {
		try {
			Integer.parseInt(year);
			return year.length() == 4;
		}
		catch(NumberFormatException e) {
			return false;
		}
	}

	/** Check if the given date is valid */
	static protected boolean isValidDate(String date) {
		try {
			Integer.parseInt(date);
			return date.length() == 8;
		}
		catch(NumberFormatException e) {
			return false;
		}
	}

	/** Check if the given sample file name is valid */
	static protected boolean isValidSampleFile(String name) {
		if(name.length() > MAX_FILENAME_LENGTH)
			return false;
		return name.endsWith(".v30") ||
		       name.endsWith(".c30") ||
		       name.endsWith(".s30") ||
		       name.endsWith(".vlog") ||
		       name.endsWith(".pr60") ||
		       name.endsWith(".pt60");
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

	/** Process a traffic data request */
	protected boolean processRequest(String pathInfo,
		HttpServletResponse response) throws IOException,
		VehicleEvent.Exception
	{
		String[] p = splitRequestPath(pathInfo);
		switch(p.length) {
		case 1:
			return processDateRequest(p[0], response);
		case 3:
			return processSampleRequest(p[0], p[1], p[2], response);
		default:
			return false;
		}
	}

	/** Process a request for the available dates for a given year */
	protected boolean processDateRequest(String year,
		HttpServletResponse response) throws IOException
	{
		if(!isValidYear(year))
			return false;
		OutputStream out = response.getOutputStream();
		try {
			OutputStreamWriter writer =
				new OutputStreamWriter(out);
			BufferedWriter w = new BufferedWriter(writer);
			writeDates(year, w);
			w.flush();
			w.close();
			return true;
		}
		finally {
			out.close();
		}
	}

	/** Write out the dates available for the given year */
	protected void writeDates(String year, Writer w) throws IOException {
		File f = new File(BASE_PATH, year);
		if(f.canRead() && f.isDirectory())
			writeDates(f, w);
	}

	/** Write out the dates available for the given directory */
	protected void writeDates(File path, Writer w) throws IOException {
		for(String name: path.list()) {
			String date = getTrafficDate(path, name);
			if(date != null)
				w.write(date + "\n");
		}
	}

	/** Get the date string for the given file */
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

	/** Process a sample data request */
	protected boolean processSampleRequest(String year, String date,
		String name, HttpServletResponse response) throws IOException,
		VehicleEvent.Exception
	{
		if(!isValidYear(year))
			return false;
		if(!isValidDate(date) || !date.startsWith(year))
			return false;
		if(!isValidSampleFile(name))
			return false;
		try {
			InputStream in = getTrafficInputStream(date, name);
			try {
				byte[] data = new byte[in.available()];
				in.read(data);
				sendData(data, response);
				return true;
			}
			finally {
				in.close();
			}
		}
		catch(FileNotFoundException e) {
			try {
				InputStream in = convertVLog(date, name);
				byte[] data = new byte[in.available()];
				in.read(data);
				sendData(data, response);
				return true;
			}
			catch(FileNotFoundException e2) {
				// Ignore
			}
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return true;
		}
	}

	/** Get an InputStream for the given date */
	static protected InputStream getTrafficInputStream(String date,
		String name) throws IOException
	{
		try {
			return getStreamZip(getDatePath(date), name);
		}
		catch(FileNotFoundException e) {
			return new FileInputStream(getDatePath(date) +
				File.separator + name);
		}
	}

	/** Get the file path to the given date */
	static protected String getDatePath(String date) {
		String year = date.substring(0, 4);
		return BASE_PATH + File.separator + year + File.separator +date;
	}

	/** Get an InputStream from a zip (traffic) file */
	static protected InputStream getStreamZip(String dir, String file)
		throws IOException
	{
		try {
			ZipFile zip = new ZipFile(dir + EXT);
			ZipEntry entry = zip.getEntry(file);
			if(entry != null)
				return zip.getInputStream(entry);
		}
		catch(ZipException e) {
			// Defer to FileNotFoundException, below
		}
		throw new FileNotFoundException(file);
	}

	/** Convert a .vlog file to another format */
	protected InputStream convertVLog(String date, String name)
		throws IOException, VehicleEvent.Exception
	{
		SampleBin bin = null;
		if(name.endsWith(".v30"))
			bin = new VolumeSampleBin();
		else if(name.endsWith(".s30"))
			bin = new SpeedSampleBin();
		if(bin != null) {
			String n = getVLogName(name);
			VehicleEventLog log = createVLog(
				getTrafficInputStream(date, n));
			log.bin30SecondSamples(bin);
			return new ByteArrayInputStream(bin.getData());
		}
		throw new FileNotFoundException(name);
	}

	/** Create and process a vehicle event log */
	protected VehicleEventLog createVLog(InputStream in)
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

	/** Get the file name with .vlog extension */
	static protected String getVLogName(String name) {
		return name.substring(0, name.length() - 3) + "vlog";
	}

	/** Send data from the given input stream to the response */
	protected void sendData(byte[] data, HttpServletResponse response)
		throws IOException
	{
		response.setContentType("application/octet-stream");
		OutputStream out = response.getOutputStream();
		try {
			out.write(data);
		}
		finally {
			out.close();
		}
	}
}
