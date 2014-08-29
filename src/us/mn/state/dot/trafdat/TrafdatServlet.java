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

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet for serving IRIS traffic sample data.  There are several valid
 * request types:
 * <pre>
 *    /district/year.json		Get dates with sample data (JSON)
 *    /district/year/date		Get sensors sampled for a date (JSON)
 *    /district/year/date/sensor.ext	Get raw sample data for a sensor
 *    /district/year/date/sensor.ext.json Get JSON sample data for a sensor
 * </pre>
 * There are also some deprecated request types:
 * <pre>
 *    /district/year			Get dates with sample data
 *    /year				Get dates with samples, "tms" district
 *    /year/date			Get sensors sampled, "tms" district
 *    /year/date/sensor.ext		Get raw sample data for "tms" district
 * </pre>
 *
 * @author john3tim
 * @author Douglas Lau
 */
public class TrafdatServlet extends HttpServlet {

	/** Maximum length of a data filename */
	static private final int MAX_FILENAME_LENGTH = 24;

	/** Default district ID */
	static private final String DEFAULT_DISTRICT = "tms";

	/** Split a request path into component parts.
	 * @param path Request path
	 * @return Array of path components. */
	static private String[] splitRequestPath(String path) {
		String[] p = splitPath(path);
		// Backward compatibility stuff:
		//    check if district path was omitted
		if (p.length > 0 && SensorArchive.isValidYear(p[0])) {
			if (path.startsWith("/"))
				return splitPath(DEFAULT_DISTRICT + path);
			else
				return splitPath(DEFAULT_DISTRICT + "/" + path);
		} else
			return p;
	}

	/** Split a path into component parts.
	 * @param path Request path
	 * @return Array of path components. */
	static private String[] splitPath(String path) {
		if (path != null) {
			while (path.startsWith("/"))
				path = path.substring(1);
			return path.split("/");
		}
		return new String[0];
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

	/** Strip the .json extension from a file name */
	static private String stripJsonExt(String name) {
		assert name.endsWith(".json");
		return name.substring(0, name.length() - 5);
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

	/** Send raw data from the given input stream to the response.
	 * @param response Servlet response object.
	 * @param in Input stream to read data from. */
	static private void sendRawData(HttpServletResponse response,
		InputStream in) throws IOException
	{
		byte[] buf = new byte[4096];
		response.setContentType("application/octet-stream");
		OutputStream out = response.getOutputStream();
		try {
			while (true) {
				int n_bytes = in.read(buf);
				if (n_bytes < 0)
					break;
				out.write(buf, 0, n_bytes);
			}
		}
		finally {
			out.close();
		}
	}

	/** Send text data from the given iterator to the response.
	 * @param response Servlet response object.
	 * @param it Iterator of values to send. */
	static private void sendTextData(HttpServletResponse response,
		Iterator<String> it) throws IOException
	{
		Writer w = createWriter(response);
		try {
			while (it.hasNext()) {
				String val = it.next();
				w.write(val + "\n");
			}
			w.flush();
		}
		finally {
			w.close();
		}
	}

	/** Send data from the given iterator to the response as JSON.
	 * @param response Servlet response object.
	 * @param it Iterator of values to send. */
	static private void sendJsonData(HttpServletResponse response,
		Iterator<String> it) throws IOException
	{
		response.setContentType("application/json");
		Writer w = createWriter(response);
		try {
			w.write('[');
			boolean first = true;
			while (it.hasNext()) {
				String val = formatJson(it.next());
				if (!first)
					w.write(',');
				w.write(val);
				first = false;
			}
			w.write(']');
			w.flush();
		}
		finally {
			w.close();
		}
	}

	/** Format a number as a JSON value.
	 * @param val Value to format.
	 * @return JSON value. */
	static private String formatJson(String val) {
		return (val != null) ? val : "null";
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
		String path = request.getPathInfo();
		try {
			if (!processRequest(path, response)) {
				response.sendError(
					HttpServletResponse.SC_BAD_REQUEST);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
			try {
				response.sendError(HttpServletResponse.
					SC_INTERNAL_SERVER_ERROR);
			}
			catch (IOException ee) {
				ee.printStackTrace();
			}
		}
	}

	/** Process a traffic data request from a client.
	 * @param path Path of requested resource.
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	private boolean processRequest(String path,
		HttpServletResponse response) throws IOException
	{
		String[] p = splitRequestPath(path);
		switch (p.length) {
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
		if (isJsonFile(year)) {
			return processJsonDates(district, stripJsonExt(year),
				response);
		}
		if (SensorArchive.isValidYear(year)) {
			SensorArchive sa = new SensorArchive(district);
			sendTextData(response, sa.lookupDates(year));
			return true;
		} else
			return false;
	}

	/** Process a request for the available dates for a given year as JSON.
	 * @param district District ID.
	 * @param year String year (4 digits, yyyy).
	 * @param response Servlet response object.
	 * @return true if request if valid, otherwise false */
	private boolean processJsonDates(String district, String year,
		HttpServletResponse response) throws IOException
	{
		if (SensorArchive.isValidYear(year)) {
			SensorArchive sa = new SensorArchive(district);
			sendJsonData(response, sa.lookupDates(year));
			return true;
		} else
			return false;
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
		if (SensorArchive.isValidYearDate(year, date)) {
			SensorArchive sa = new SensorArchive(district);
			sendJsonData(response, sa.lookup(date));
			return true;
		} else
			return false;
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
		if (SensorArchive.isValidYearDate(year, date) &&
		    isFileNameValid(name))
		{
			try {
				return processSampleRequest(district, date,
					name, response);
			}
			catch (FileNotFoundException e) {
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
		if (isJsonFile(name)) {
			return processJsonRequest(district, date,
				stripJsonExt(name), response);
		} else if (SensorArchive.isValidSampleFile(name)) {
			SensorArchive sa = new SensorArchive(district);
			InputStream in = sa.sampleInputStream(date, name);
			try {
				sendRawData(response, in);
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
		if (SensorArchive.isBinnedFile(name)) {
			SensorArchive sa = new SensorArchive(district);
			sendJsonData(response, sa.sampleIterator(date, name));
			return true;
		} else
			return false;
	}
}
