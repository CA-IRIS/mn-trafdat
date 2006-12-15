
/**
 * Bin for storing volume sample data
 *
 * @author Douglas Lau
 */
public class VolumeSampleBin implements SampleBin {

	/** Binned 30-second volume data */
	protected final byte[] vol = new byte[SAMPLES_PER_DAY];

	/** Create a new volume sample bin */
	public VolumeSampleBin() {
		for(int i = 0; i < SAMPLES_PER_DAY; i++)
			vol[i] = SampleData.MISSING_DATA;
	}

	/** Add one data sample to the bin */
	public void addSample(SampleData sam) {
		byte v = (byte)sam.getVolume();
		if(v >= 0) {
			int p = sam.getPeriod();
			if(p >= 0 && p < SAMPLES_PER_DAY)
				vol[p] = v;
		}
	}

	/** Get the binned data */
	public byte[] getData() {
		return vol;
	}
}
