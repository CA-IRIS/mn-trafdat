/*
 * Project: Trafdat
 * Copyright (C) 2007  Minnesota Department of Transportation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
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
