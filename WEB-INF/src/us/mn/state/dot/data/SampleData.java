
/**
 * Class to bin vehicle event data into sample data periods.
 *
 * @author Douglas Lau
 */
public class SampleData {

	/** Missing sample data is indicated by negative value */
	static protected final int MISSING_DATA = -1;

	/** Was there a reset during this sample period? */
	protected boolean reset = false;

	/** Period number (sample period during day) */
	protected int period = -1;

	/** Total volume for the sample data period */
	protected int volume = 0;

	/** Sum of vehicle speeds for the sample data period */
	protected int speed_sum = 0;

	/** Count of vehicle speeds for the sample data period */
	protected int speed_cnt = 0;

	/** Get the sample period number */
	public int getPeriod() {
		return period;
	}

	/** Get the binned volume for the sample period */
	public int getVolume() {
		if(volume < 128 && !reset)
			return volume;
		else
			return MISSING_DATA;
	}

	/** Get the binned speed for the sample period */
	public int getSpeed() {
		if(speed_cnt > 0 && !reset) {
			int speed = Math.round(speed_sum / speed_cnt);
			if(speed < 128)
				return speed;
		}
		return MISSING_DATA;
	}

	/** Add one vehicle event to the sample period */
	public void addEvent(VehicleEvent ev) {
		volume += 1;
		Integer s = ev.getSpeed();
		if(s != null) {
			speed_sum += s;
			speed_cnt += 1;
		}
	}

	/** Set the reset flag */
	public void setReset() {
		reset = true;
	}

	/** Clear the sample data for a new period */
	public void clear(int p) {
		reset = false;
		period = p;
		volume = 0;
		speed_sum = 0;
		speed_cnt = 0;
	}
}
