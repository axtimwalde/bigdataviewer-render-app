/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bdv.render;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class StackInfo {

	final static public class StackId {

		public String owner;
		public String project;
		public String stack;
	}

	final static public class Version {

//		public Date createTimestamp;
		public int cycleNumber;
		public int cycleStepNumber;
		public double stackResolutionX;
		public double stackResolutionY;
		public double stackResolutionZ;
	}

	final static public class Stats {

		public Bounds stackBounds;
		public int sectionCount;
		public double nonIntegralSectionCount;
		public long tileCount;
		public long transformCount;
		public int minTileWidth;
		public int maxTileWidth;
		public int minTileHeight;
		public int maxTileHeight;
	}

	public StackId stackId;
	public String state;
//	public Date lastModifiedTimestamp;
	public int currentVersionNumber;
	public Version currentVersion;
	public Stats stats;
}