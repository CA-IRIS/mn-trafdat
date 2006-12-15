
/**
 * Bin for storing speed sample data
 *
 * @author Douglas Lau
 */
public class SpeedSampleBin implements SampleBin {

	/** Binned 30-second speed data */
	protected final byte[] spd = new byte[SAMPLES_PER_DAY];

	/** Create a new speed sample bin */
	public SpeedSampleBin() {
		for(int i = 0; i < SAMPLES_PER_DAY; i++)
			spd[i] = SampleData.MISSING_DATA;
	}

	/** Add one data sample to the bin */
	public void addSample(SampleData sam) {
		byte s = (byte)sam.getSpeed();
		if(s >= 0) {
			int p = sam.getPeriod();
			if(p >= 0 && p < SAMPLES_PER_DAY)
				spd[p] = s;
		}
	}

	/** Get the binned data */
	public byte[] getData() {
		return spd;
	}
}
