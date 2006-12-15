package us.mn.state.dot.data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
	}

	/** Process a traffic data request */
	protected boolean processRequest(String pathInfo,
		HttpServletResponse response) throws IOException
	{
		if(pathInfo == null)
			return false;
		String p = pathInfo.toLowerCase();
		String[] t = p.split("/");
		if(t.length == 1)
			return processDateRequest(t[0], response);
		if(t.length != 3)
			return false;
		String year = t[1];
		String date = t[2];
		String name = t[3];
		if(!isValidYear(year))
			return false;
		if(!isValidDate(date) || !date.startsWith(year))
			return false;
		if(!isValidName(name))
			return false;
		try {
			InputStream in = getTrafficInputStream(date, name);
			try {
				sendData(in, response);
			}
			finally {
				in.close();
			}
		}
		catch(FileNotFoundException e) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
		return true;
	}

	/** Send data from the given input stream to the response */
	protected void sendData(InputStream in, HttpServletResponse response)
		throws IOException
	{
		response.setContentType("application/octet-stream");
		OutputStream out = response.getOutputStream();
		try {
			byte[] data = new byte[in.available()];
			in.read(data);
			out.write(data);
		}
		finally {
			out.close();
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

	/** Get an InputStream for the given date */
	static protected InputStream getTrafficInputStream(String date,
		String name) throws IOException
	{
		String year = date.substring(0, 4);
		try {
			return new FileInputStream(BASE_PATH + File.separator +
				year + File.separator + date + File.separator +
				name);
		}
		catch(FileNotFoundException e) {
			return getStreamZip(BASE_PATH + File.separator + year +
				File.separator + date, name);
		}
	}

	/** Get an InputStream from a zip (traffic) file */
	static protected InputStream getStreamZip(String dir, String file)
		throws IOException
	{
		ZipFile zip = new ZipFile(dir + EXT);
		ZipEntry entry = zip.getEntry(file);
		if(entry != null)
			return zip.getInputStream(entry);
		throw new FileNotFoundException(file);
	}
}
