import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Vehicle event log reader / processor.
 *
 * @author Douglas Lau
 */
public class VehicleEventLog {

	/** Number of 30-second samples per day */
	static protected final int SAMPLES_PER_DAY = 2880;

	/** List of all vehicle events in the log */
	protected final LinkedList<VehicleEvent> events =
		new LinkedList<VehicleEvent>();

	/** Create a new vehicle event log */
	public VehicleEventLog(BufferedReader reader) {
		String line = reader.readLine();
		while(line != null) {
			events.append(new VehicleEvent(line));
			line = reader.readLine();
		}
	}

	/** Propogate timestamps forward to following events */
	public void propogateStampsForward() {
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
	public void interpolateMissingStamps() {
		Integer stamp = null;
		LinkedList<VehicleEvent> ev = new LinkedList<VehicleEvent>();
		for(VehicleEvent e: events) {
			Integer s = e.getStamp();
			if(s == null)
				ev.append(e);
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
				int p = get30SecondPeriod(e.getStamp());
				if(p > sam.getPeriod()) {
					bin.addSample(sam);
					sam.clear(p);
				}
				sam.addEvent(e);
			}
		}
		bin.addSample(sam);
	}
}
