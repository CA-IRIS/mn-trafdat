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
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUtils;

/**
 * @author john3tim
 */
public class DataServer extends HttpServlet {

	/** Maximum length of a data filename */
	protected final int MAX_FILENAME_LENGTH = 20;
	
	/** Traffic file extension */
	static protected final String EXT = ".traffic";

	/** Path to directory containing traffic data files */
	protected final String basePath = "/data/traffic";
	
	/** Initializes the servlet */
	public void init(ServletConfig config) throws ServletException {
	}

	public void doGet( HttpServletRequest request,
			HttpServletResponse response )
	{
		String pathInfo = request.getPathInfo();
		if(isDataRequest(pathInfo)){
			processDataRequest(pathInfo, response);
		}else if(isDateRequest(pathInfo)){
			processDateRequest(pathInfo, response);
		}else{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}

	protected boolean isDataRequest(String p){
		if(p == null) return false;
		p = p.toLowerCase();
		StringTokenizer t = new StringTokenizer(p, "/", false);
		if(t.countTokens() != 3) return false;
		t.nextToken(); //throw away the year
		t.nextToken(); //throw away the date
		String name = t.nextToken();
		if(name.length() > MAX_FILENAME_LENGTH) return false;
		return (name.endsWith(".v30") ||
				name.endsWith(".c30") ||
				name.endsWith(".s30"));
	}
	
	protected boolean isDateRequest(String p){
		if(p == null) return false;
		p = p.toLowerCase();
		StringTokenizer t = new StringTokenizer(p, "/", false);
		if(t.countTokens() != 1) return false;
		try{
			Integer.parseInt(t.nextToken());
		}catch(NumberFormatException nfe){
			return false;
		}
		return true;
	}

	protected void processDataRequest(String p,
			HttpServletResponse response){
		StringTokenizer t = new StringTokenizer(p, "/", false);
		String y = t.nextToken();
		String d = t.nextToken();
		String f = t.nextToken();
		InputStream in = null;
		try{
			in = new FileInputStream(basePath + p);
		}catch(IOException ioe){
			in = getStreamZip(
					basePath + File.separator + y + File.separator + d, f);
		}
		if(in == null){
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}else{
			sendData(in, response);
		}
	}
	
	protected void processDateRequest(String p,
			HttpServletResponse response){
		OutputStream out = null;
		try{
			out = response.getOutputStream();
			OutputStreamWriter writer = new OutputStreamWriter(out);
			BufferedWriter w = new BufferedWriter(writer);
			String[] dates = getDates(p);
			for(int i=0; i<dates.length; i++){
				w.write(dates[i] + "\n");
			}
			w.flush();
			w.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	protected String[] getDates(String p){
		Collection dates = new Vector();
		StringTokenizer t = new StringTokenizer(p, "/", false);
		String year = t.nextToken();
		if(year.length() != 4) return new String[0];
		File f = new File(basePath, year);
		if(!f.canRead() || !f.isDirectory()) return new String[0];
		String[] list = f.list();
		for(int i = 0; i < list.length; i++) {
			String date = checkTrafficFile(f, list[i]);
			if(date != null) dates.add(date);
		}
		return (String[])(dates.toArray(new String[0]));
	}
	
	/** Check if the given file is a vaild traffic file */
	protected String checkTrafficFile(File p, String n) {
		if(n.length() < 8) return null;
		String date = n.substring(0, 8);
		try { Integer.parseInt(date); }
		catch(NumberFormatException e) { return null; }
		File file = new File(p, n);
		if(!file.canRead()) return null;
		if(n.length() == 8 && file.isDirectory()) return date;
		if(n.length() == 16 && n.endsWith(EXT)) return date;
		return null;
	}

	protected void sendData(InputStream in, HttpServletResponse response){
		response.setContentType("application/octet-stream");
		OutputStream out = null;
		try{
			if(in.available() <= 0) return;
			out = response.getOutputStream();
			byte[] data = new byte[in.available()];
			in.read(data);
			out.write(data);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			try{
				in.close();
				out.flush();
				out.close();
			}catch(Exception e2){
			}
		}
	}
	
	/** Get an InputStream from a zip (traffic) file */
	protected InputStream getStreamZip( String dir, String file ){
		try {
			ZipFile zip = new ZipFile( dir + EXT );
			ZipEntry entry = zip.getEntry( file );
			if( entry == null ) throw new
				FileNotFoundException( file );
			return zip.getInputStream( entry );
		}catch(IOException ioe){
			return null;
		}
	}

}
