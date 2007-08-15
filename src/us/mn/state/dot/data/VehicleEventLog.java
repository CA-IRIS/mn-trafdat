package us.mn.state.dot.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Vehicle event log reader / processor.
 *
 * @author Douglas Lau
 */
public class VehicleEventLog {

	/** List of all vehicle events in the log */
	protected final LinkedList<VehicleEvent> events =
		new LinkedList<VehicleEvent>();

	/** Create a new vehicle event log */
	public VehicleEventLog(BufferedReader reader) throws IOException {
		String line = reader.readLine();
		while(line != null) {
			events.add(new VehicleEvent(line));
			line = reader.readLine();
		}
	}

	/** Propogate timestamps forward to following events */
	public void propogateStampsForward() throws VehicleEvent.Exception {
		Integer stamp = null;
		for(VehicleEvent e: events) {
			if(stamp != null)
				e.setPreviousStamp(stamp);
			stamp = e.getStamp();
		}
	}

	/** Propogate timestamps backward to previous events */
	public void propogateStampsBackward() {
		Integer stamp = null;
		ListIterator<VehicleEvent> it =
			events.listIterator(events.size());
		while(it.hasPrevious()) {
			VehicleEvent e = it.previous();
			e.setStamp(stamp);
			stamp = e.getPreviousStamp();
		}
	}

	/** Interpolate timestamps in gaps where they are missing */
	public void interpolateMissingStamps() throws VehicleEvent.Exception {
		Integer stamp = null;
		LinkedList<VehicleEvent> ev = new LinkedList<VehicleEvent>();
		for(VehicleEvent e: events) {
			Integer s = e.getStamp();
			if(s == null)
				ev.add(e);
			else if(!ev.isEmpty()) {
				if(stamp != null) {
					int gap = s - stamp;
					int t = ev.size() + 1;
					int headway = Math.round(gap / t);
					for(VehicleEvent v: ev) {
						v.setHeadway(headway);
						v.setPreviousStamp(stamp);
						stamp = v.getStamp();
					}
				}
				ev.clear();
			}
			if(s != null)
				stamp = s;
		}
	}

	/** Get the 30-second period for the given timestamp (ms) */
	protected int getPeriod30Second(int ms) throws VehicleEvent.Exception {
		int p = ms / 30000;
		if(p < 0 || p > SampleBin.SAMPLES_PER_DAY)
			throw new VehicleEvent.Exception();
		return p;
	}

	/** Bin vehicle event data into 30 second samples */
	public void bin30SecondSamples(SampleBin bin)
		throws VehicleEvent.Exception
	{
		SampleData sam = new SampleData();
		for(VehicleEvent e: events) {
			e.check();
			if(e.isReset())
				sam.setReset();
			else {
				int p = getPeriod30Second(e.getStamp());
				int pp = sam.getPeriod();
				while(p > pp) {
					bin.addSample(sam);
					sam.clear(++pp);
				}
				sam.addEvent(e);
			}
		}
		bin.addSample(sam);
	}
}
