/**
 *
 */
package bdv.render;

import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;

import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.type.numeric.ARGBType;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class SliceLoader implements CellLoader<ARGBType> {

	protected final String baseUrl;
	protected final String owner;
	protected final String project;
	protected final String stack;
	protected boolean average;
	protected boolean filter;
	protected final long[] offset;
	protected final int iScale;
	protected final double scale;
	protected final long zScale;
	protected MipmapSourceRewriter rewriter;

	public SliceLoader(
			final String baseUrl,
			final String owner,
			final String project,
			final String stack,
			final boolean average,
			final boolean filter,
			final long[] offset,
			final int scaleLevel,
			final long zScale,
			final MipmapSourceRewriter rewriter) {

		this.baseUrl = baseUrl;
		this.owner = owner;
		this.project = project;
		this.stack = stack;
		this.average = average;
		this.filter = filter;
		this.offset = offset;
		iScale = 1 << scaleLevel;
		scale = 1.0 / iScale;
		this.zScale = zScale;
		this.rewriter = rewriter;
	}

	@Override
	public void load(SingleCellArrayImg<ARGBType, ?> cell) throws Exception {

		final int[] data = (int[])cell.getStorageArray();

		final long x = cell.min(0) * iScale + offset[0];
		final long y = cell.min(1) * iScale + offset[1];
		final long w = cell.dimension(0) * iScale;
		final long h = cell.dimension(1) * iScale;
		final long z;

		if (average && zScale > 1) {

			z = cell.min(2) * zScale + offset[2];

			final long[] rs = new long[data.length], gs = new long[data.length], bs = new long[data.length];

			for (long dz = 0; dz < zScale; ++dz) {
				final BufferedImage image = Rest.renderImage(
						baseUrl,
						owner,
						project,
						stack,
						x,
						y,
						z + dz,
						w,
						h,
						scale,
						filter,
						rewriter);
					final PixelGrabber pg = new PixelGrabber(image, 0, 0, (int)cell.dimension(0), (int)cell.dimension(1), data, 0, (int)cell.dimension(0));
					pg.grabPixels();
					for (int i = 0; i < data.length; ++i) {
						rs[i] += (data[i] >> 16) & 0xff;
						gs[i] += (data[i] >> 8) & 0xff;
						bs[i] += data[i] & 0xff;
					}
				}
				for (int i = 0; i < data.length; ++i) {
					final int r = (int) (rs[i] / (double)zScale);
					final int g = (int) (gs[i] / (double)zScale);
					final int b = (int) (bs[i] / (double)zScale);

					data[i] = ((((r << 8) | g) << 8) | b) | 0xff000000;
				}
			} else {

				z = cell.min(2) + offset[2];

				final BufferedImage image = Rest.renderImage(
						baseUrl,
						owner,
						project,
						stack,
						x,
						y,
						z,
						w,
						h,
						scale,
						filter,
						rewriter);
				final PixelGrabber pg = new PixelGrabber(image, 0, 0, (int)cell.dimension(0), (int)cell.dimension(1), data, 0, (int)cell.dimension(0));
				pg.grabPixels();
		}
	}
}
