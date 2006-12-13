/*
 * Created on Apr 25, 2005
 */
package us.mn.state.dot.data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.StringTokenizer;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
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

	/** Check if the given year is valid */
	static protected boolean isValidYear(String year) {
		try {
			Integer.parseInt(year);
			return year.length() == 4;
		} catch(NumberFormatException e) {
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

	/** Check if the given filename is valid */
	static protected boolean isValidName(String name) {
		if(name.length() > MAX_FILENAME_LENGTH)
			return false;
		return (name.endsWith(".v30") ||
			name.endsWith(".c30") ||
			name.endsWith(".s30") ||
			name.endsWith(".vlog"));
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
		if(!processRequest(pathInfo, response))
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
	}

	/** Process a traffic data request */
	protected boolean processRequest(String pathInfo,
		HttpServletResponse response)
	{
		if(pathInfo == null)
			return false;
		String p = pathInfo.toLowerCase();
		StringTokenizer t = new StringTokenizer(p, "/", false);
		int n_tokens = t.countTokens();
		if(n_tokens == 1)
			return processDateRequest(t.nextToken(), response);
		if(n_tokens != 3)
			return false;
		String year = t.nextToken();
		if(!isValidYear(year))
			return false;
		String date = t.nextToken();
		if(!isValidDate(date) || !date.startsWith(year))
			return false;
		String name = t.nextToken();
		if(!isValidName(name))
			return false;
		InputStream in = getTrafficInputStream(date, name);
		if(in == null)
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		else
			sendData(in, response);
		return true;
	}

	/** Send data from the given input stream to the response */
	protected void sendData(InputStream in, HttpServletResponse response) {
		response.setContentType("application/octet-stream");
		OutputStream out = null;
		try {
			if(in.available() <= 0)
				return;
			out = response.getOutputStream();
			byte[] data = new byte[in.available()];
			in.read(data);
			out.write(data);
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			try {
				in.close();
				out.flush();
				out.close();
			} catch(Exception e2) {
				// Ignore
			}
		}
	}

	protected boolean processDateRequest(String year,
		HttpServletResponse response)
	{
		if(!isValidYear(year))
			return false;
		try {
			OutputStream out = response.getOutputStream();
			OutputStreamWriter writer = new OutputStreamWriter(out);
			BufferedWriter w = new BufferedWriter(writer);
			LinkedList<String> dates = getDates(year);
			for(String date: dates) 
				w.write(date + "\n");
			w.flush();
			w.close();
			return true;
		} catch(Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	protected LinkedList<String> getDates(String year) {
		File f = new File(BASE_PATH, year);
		if(f.canRead() && f.isDirectory())
			return getDates(f);
		else
			return new LinkedList<String>();
	}

	protected LinkedList<String> getDates(File path) {
		LinkedList<String> dates = new LinkedList<String>();
		String[] list = path.list();
		for(int i = 0; i < list.length; i++) {
			String date = getTrafficDate(path, list[i]);
			if(date != null)
				dates.add(date);
		}
		return dates;
	}

	/** Get the date string for the given file */
	protected String getTrafficDate(File path, String name) {
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

	/** Get an InputStream for the given date */
	static protected InputStream getTrafficInputStream(String date,
		String name)
	{
		String year = date.substring(0, 4);
		try {
			return new FileInputStream(BASE_PATH + File.separator +
				year + File.separator + date + File.separator +
				name);
		} catch(IOException e) {
			return getStreamZip(BASE_PATH + File.separator + year +
				File.separator + date, name);
		}
	}

	/** Get an InputStream from a zip (traffic) file */
	static protected InputStream getStreamZip(String dir, String file) {
		try {
			ZipFile zip = new ZipFile(dir + EXT);
			ZipEntry entry = zip.getEntry(file);
			if(entry != null)
				return zip.getInputStream(entry);
		}
		catch(IOException e) {
			// Ignore
		}
		return null;
	}
}
