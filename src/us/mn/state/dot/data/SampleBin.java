package us.mn.state.dot.data;

/**
 * Bin for storing sample data
 *
 * @author Douglas Lau
 */
public interface SampleBin {

	/** Number of 30-second samples per day */
	int SAMPLES_PER_DAY = 2880;

	/** Add one data sample to the bin */
	void addSample(SampleData sam);

	/** Get the sample data */
	byte[] getData();
}
