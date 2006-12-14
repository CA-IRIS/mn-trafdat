package us.mn.state.dot.data;

/**
 * Vehicle event class
 *
 * @author Douglas Lau
 */
public class VehicleEvent {

	/** Vehicle event exception */
	public class Exception extends java.lang.Exception { }

	/** Parse an integer from a vehicle log */
	static protected Integer parseInt(String v) {
		try {
			return Integer.valueOf(v);
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	/** Parse a time stamp from a vehicle log */
	static protected Integer parseStamp(String v) {
		String[] t = v.split(":");
		if(t.length != 3)
			return null;
		try {
			int hour = Integer.parseInt(t[0]);
			int minute = Integer.parseInt(t[1]);
			int second = Integer.parseInt(t[2]);
			if(hour < 0 || hour > 23)
				return null;
			if(minute < 0 || minute > 59)
				return null;
			if(second < 0 || second > 59)
				return null;
			int ms = hour * 3600 + minute * 60 + second;
			return ms * 1000;
		}
		catch(NumberFormatException e) {
			return null;
		}
	}

	/** Is this a reset event? */
	protected boolean reset = false;

	/** Duration vehicle was over detector (ms) */
	protected Integer duration = null;

	/** Headway from start of previous vehicle to this one (ms) */
	protected Integer headway = null;

	/** Time stamp of this event (ms of day 0 - 86.4 million) */
	protected Integer stamp = null;

	/** Vehicle speed (mph) */
	protected Integer speed = null;

	/** Create a new vehicle event */
	public VehicleEvent(String line) throws VehicleEvent.Exception {
		String[] f = line.trim().split(",");
		if(f.length == 1 && f[0].equals("*"))
			reset = true;
		if(f.length > 0)
			duration = parseInt(f[0]);
		if(f.length > 1)
			headway = parseInt(f[1]);
		if(f.length > 2)
			stamp = parseStamp(f[2]);
		if(f.length > 3)
			speed = parseInt(f[3]);
	}

	/** Get a string representation of the vehicle event */
	public String toString() {
		if(reset)
			return "*";
		StringBuilder b = new StringBuilder();
		b.append(duration);
		b.append(',');
		b.append(headway);
		b.append(',');
		b.append(stamp);
		b.append(',');
		b.append(speed);
		return b.toString();
	}

	/** Get a timestamp for the previous vehicle event */
	public Integer getPreviousStamp() {
		if(stamp == null || headway == null)
			return null;
		return stamp - headway + 999;
	}

	/** Set headway/timestamp based on previous vehicle stamp */
	public void setPreviousStamp(int pstamp) throws VehicleEvent.Exception {
		if(headway != null)
			setStamp(pstamp + headway);
		if(stamp != null)
			setHeadway(stamp - pstamp);
	}

	/** Set the timestamp if not already set */
	public void setStamp(int s) {
		if(stamp == null)
			stamp = s;
	}

	/** Set the headway if not already set */
	public void setHeadway(int h) throws VehicleEvent.Exception {
		if(headway == null) {
			if(h <= 0)
				throw new VehicleEvent.Exception();
			headway = h;
		}
	}

	/** Check the vehicle event is valid */
	public void check() throws VehicleEvent.Exception {
		if(stamp == null && !reset)
			throw new VehicleEvent.Exception();
	}
}
