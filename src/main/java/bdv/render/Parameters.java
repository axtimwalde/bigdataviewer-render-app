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

import com.beust.jcommander.Parameter;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class Parameters {

	final static public String ownerFormat = "%s/owner/%s";
	final static public String stackListFormat = ownerFormat + "/stacks";
	final static public String stackFormat = ownerFormat + "/project/%s/stack/%s";
	final static public String stackBoundsFormat = stackFormat  + "/bounds";
	final static public String boundingBoxFormat = stackFormat + "/z/%d/box/%d,%d,%d,%d,%f";
	final static public String renderParametersFormat = boundingBoxFormat + "/render-parameters";

	@Parameter(names = { "--base_url", "-b" }, description = "base URL")
//	public String baseUrl = "http://tem-services.int.janelia.org:8080/render-ws/v1";
	public String baseUrl = "https://render-dev-eric.neurodata.io/render-ws/v1";

	@Parameter(names = { "--owner", "-o" }, description = "owner")
//	public String owner = "flyTEM";
	public String owner = "Forrest";

	@Parameter(names = { "--project", "-p" }, description = "project")
	public String project = "FAFB00";

	@Parameter(names = { "--stack", "-s" }, description = "stack")
	public String stack = "v12_align_tps";

	@Parameter(names = { "--filter", "-f" }, description = "filter")
	public boolean filter = false;

	@Parameter(names = { "--average_z", "-z" }, description = "average z")
	public boolean averageZ = false;

	@Parameter(names = { "--tile_width", "-w" }, description = "tile width")
	public int tileWidth = 256;

	@Parameter(names = { "--tile_height", "-h" }, description = "tile height")
	public int tileHeight = 256;

	@Parameter(names = { "--rewrite", "-r" }, description = "rewrite mipmap URLs to render requests")
	public boolean rewrite = true;

	@Override
	public Parameters clone() {
		final Parameters copy = new Parameters();
		copy.baseUrl = baseUrl;
		copy.owner = owner;
		copy.project = project;
		copy.stack = stack;
		copy.tileWidth = tileWidth;
		copy.tileHeight = tileHeight;
		copy.filter = filter;
		copy.averageZ = averageZ;
		copy.rewrite = rewrite;

		return copy;
	}
}