package bdv.img.render;

import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import org.janelia.alignment.Render;
import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.util.ImageProcessorCache;

import com.google.gson.Gson;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.img.cell.CellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Fraction;

public class RenderAppImageLoader extends AbstractViewerSetupImgLoader<ARGBType, VolatileARGBType> implements ViewerImgLoader {

	static interface SliceLoader {

		public void loadSlice(
				final int level,
				final int[] data,
				final long[] min,
				final int[] dimensions) throws InterruptedException;
	}

	final protected int setupId;

	final private int tileWidth;
	final private int tileHeight;

	final private int numScales;

	final private double[][] mipmapResolutions;

	final private AffineTransform3D[] mipmapTransforms;

	final private long[][] imageDimensions;

	final private int[] zScales;

	final private long[] offset;

	final private VolatileGlobalCellCache cache;

	final protected RenderAppVolatileIntArrayLoader arrayLoader;

	final static public int getNumScales(
			long width,
			long height,
			final long tileWidth,
			final long tileHeight) {
		int i = 1;

		while ((width >>= 1) > tileWidth && (height >>= 1) > tileHeight)
			++i;

		return i;
	}

	public RenderAppImageLoader(
			final Parameters p,
			final Gson gson,
			final int setupId,
			final double zScale,
			final int numScales) throws MalformedURLException, IOException {

		super(new ARGBType(), new VolatileARGBType());

		this.setupId = setupId;
		tileWidth = p.tileWidth;
		tileHeight = p.tileHeight;

		final String boundsQuery = String.format(
				Parameters.stackBoundsFormat,
				p.baseUrl,
				p.owner,
				p.project,
				p.stack);

		try (final InputStreamReader reader = new InputStreamReader(new URL(boundsQuery).openStream())) {

			final Bounds bounds = gson.fromJson(reader, Bounds.class);
//			System.out.println(gson.toJson(bounds));

			offset = new long[] { (long) bounds.minX, (long) bounds.minY, (long) bounds.minZ };

			final FinalDimensions size = new FinalDimensions(
					new long[]{
							(long)Math.ceil(bounds.maxX - bounds.minX + 1),
							(long)Math.ceil(bounds.maxY - bounds.minY + 1),
							(long)Math.ceil(bounds.maxZ - bounds.minZ + 1) });

			if (numScales < 1)
				this.numScales = getNumScales(size.dimension(0), size.dimension(1), tileWidth, tileHeight);
			else
				this.numScales = numScales;

			mipmapResolutions = new double[numScales][];
			imageDimensions = new long[numScales][];
			mipmapTransforms = new AffineTransform3D[numScales];
			zScales = new int[numScales];
			for (int l = 0; l < numScales; ++l) {

				final int sixy = 1 << l;
				final int siz = p.averageZ ? Math.max(1, (int) Math.round(sixy / zScale)) : 1;

				mipmapResolutions[l] = new double[] { sixy, sixy, siz };
				imageDimensions[l] = new long[] { size.dimension(0) >> l, size.dimension(1) >> l, size.dimension(2) / siz };
				zScales[l] = siz;

				final AffineTransform3D mipmapTransform = new AffineTransform3D();

				mipmapTransform.set(sixy, 0, 0);
				mipmapTransform.set(sixy, 1, 1);
				mipmapTransform.set(zScale * siz, 2, 2);

//				mipmapTransform.set(0.5 * (sixy - 1), 0, 3);
//				mipmapTransform.set(0.5 * (sixy - 1), 1, 3);
				mipmapTransform.set(0.5 * (zScale * siz - 1), 2, 3);

				mipmapTransforms[l] = mipmapTransform;
			}

			cache = new VolatileGlobalCellCache(1, 1, numScales, 45);

			final SliceLoader sliceLoader;
			if (p.averageZ) {
				sliceLoader = new SliceLoader() {

					@Override
					public void loadSlice(
							final int level,
							final int[] data,
							final long[] min,
							final int[] dimensions) throws InterruptedException {

						final int iScale = 1 << level;
						final double scale = 1.0 / iScale;
						final long x = min[ 0 ] * iScale + offset[0];
						final long y = min[ 1 ] * iScale + offset[1];
						final int w = dimensions[0] * iScale;
						final int h = dimensions[1] * iScale;
						final long z;

						if (zScales[level] > 1) {

							z = (long)min[2] * zScales[level] + offset[2];

							final long[] rs = new long[data.length], gs = new long[data.length], bs = new long[data.length];

							for (long dz = 0; dz < zScales[level]; ++dz) {
								final BufferedImage image = renderImage(p, x, y, z + dz, w, h, scale, p.filter);
								final PixelGrabber pg = new PixelGrabber(image, 0, 0, dimensions[0], dimensions[1], data, 0, dimensions[0]);
								pg.grabPixels();
								for (int i = 0; i < data.length; ++i) {
									rs[i] += (data[i] >> 16) & 0xff;
									gs[i] += (data[i] >> 8) & 0xff;
									bs[i] += data[i] & 0xff;
								}
							}
							for (int i = 0; i < data.length; ++i) {
								final int r = (int) (rs[i] / zScales[level]);
								final int g = (int) (gs[i] / zScales[level]);
								final int b = (int) (bs[i] / zScales[level]);

								data[i] = ((((r << 8) | g) << 8) | b) | 0xff000000;
							}
						} else {

							z = min[2] + offset[2];

							final BufferedImage image = renderImage(p, x, y, z, w, h, scale, p.filter);
							final PixelGrabber pg = new PixelGrabber(image, 0, 0, dimensions[0], dimensions[1], data, 0, dimensions[0]);
							pg.grabPixels();
						}

//						if (isAllTheSame(data))
//							System.out.println("level = " + level + ", min = " + Arrays.toString(min) + ", (x, y, z) = " + Arrays.toString(new long[] { x, y, z }) + ": all pixels are " + data[0]);
//						else
//							System.out.print("level = " + level + ", min = " + Arrays.toString(min) + ", (x, y, z) = " + Arrays.toString(new long[] { x, y, z }) + " ");
					}
				};
			}
			else {
				sliceLoader = new SliceLoader() {

					@Override
					public void loadSlice(
							final int level,
							final int[] data,
							final long[] min,
							final int[] dimensions) throws InterruptedException {

						final int iScale = 1 << level;
						final double scale = 1.0 / iScale;
						final long x = min[0] * iScale + offset[0];
						final long y = min[1] * iScale + offset[1];
						final long z = min[2] + offset[2];
						final long w = dimensions[0] * iScale;
						final long h = dimensions[1] * iScale;

						final BufferedImage image = renderImage(p, x, y, z, w, h, scale, p.filter);
						final PixelGrabber pg = new PixelGrabber(image, 0, 0, dimensions[0], dimensions[1], data, 0, dimensions[0]);
						pg.grabPixels();

//						if (isAllTheSame(data))
//							System.out.println("no averaging, level = " + level + ", min = " + Arrays.toString(min) + ", (x, y, z) = " + Arrays.toString(new long[] { x, y, z }) + ": all pixels are " + data[0]);
//						else
//							System.out.print("no averaging, level = " + level + ", min = " + Arrays.toString(min) + ", (x, y, z) = " + Arrays.toString(new long[] { x, y, z }) + " ");
					}
				};
			}

			arrayLoader = new RenderAppVolatileIntArrayLoader(
					p,
					sliceLoader,
					zScales);
		}
	}

	public RenderAppImageLoader(
			final Parameters p,
			final Gson gson,
			final int setupId,
			final int tileWidth,
			final int tileHeight,
			final double zScale,
			final boolean averageSlices,
			final boolean filter) throws MalformedURLException, IOException {

		this(p, gson, setupId, zScale, -1);
	}

	final static private boolean isAllTheSame(final int[] data) {

		final int reference = data[0];
		for (final int i : data) {
			if (i != reference)
				return false;
		}
		return true;
	}

	final static private BufferedImage renderImage(
			final Parameters p,
			final long x,
			final long y,
			final long z,
			final long w,
			final long h,
			final double scale,
			final boolean filter) {

		final String renderParametersUrlString = String.format(
				Parameters.renderParametersFormat,
				p.baseUrl,
				p.owner,
				p.project,
				p.stack,
				z,
				x,
				y,
				w,
				h,
				scale);

		final RenderParameters renderParameters = RenderParameters.loadFromUrl(renderParametersUrlString);
		renderParameters.setDoFilter(filter);

        final BufferedImage image = renderParameters.openTargetImage();
        Render.render(renderParameters, image, new ImageProcessorCache());

        return image;
	}

	@Override
	public RandomAccessibleInterval<ARGBType> getImage(final int timepointId, final int level, final ImgLoaderHint... hints) {
		final CachedCellImg<ARGBType, VolatileIntArray> img = prepareCachedImage(timepointId, level, LoadingStrategy.BLOCKING);
		final ARGBType linkedType = new ARGBType(img);
		img.setLinkedType(linkedType);
		return img;
	}

	@Override
	public RandomAccessibleInterval<VolatileARGBType> getVolatileImage(final int timepointId, final int level, final ImgLoaderHint... hints) {
		final CachedCellImg<VolatileARGBType, VolatileIntArray> img = prepareCachedImage(timepointId, level, LoadingStrategy.VOLATILE);
		final VolatileARGBType linkedType = new VolatileARGBType(img);
		img.setLinkedType(linkedType);
		return img;
	}

	@Override
	public ViewerSetupImgLoader<?, ?> getSetupImgLoader(final int setupId) {
		return this;
	}

	@Override
	public double[][] getMipmapResolutions() {
		return mipmapResolutions;
	}

	@Override
	public int numMipmapLevels() {
		return numScales;
	}

	/**
	 * (Almost) create a {@link CellImg} backed by the cache. The created image
	 * needs a {@link NativeImg#setLinkedType(net.imglib2.type.Type) linked
	 * type} before it can be used. The type should be either {@link ARGBType}
	 * and {@link VolatileARGBType}.
	 */
	protected <T extends NativeType<T>> CachedCellImg<T, VolatileIntArray> prepareCachedImage(
			final int timepointId,
			final int level,
			final LoadingStrategy loadingStrategy) {

		final long[] dimensions = imageDimensions[level];
		final int[] cellDimensions = new int[] { tileWidth, tileHeight, 1 };

		final int priority = 0;
		final CacheHints cacheHints = new CacheHints(loadingStrategy, priority, false);
		final CellCache<VolatileIntArray> c = cache.new VolatileCellCache<VolatileIntArray>(timepointId, setupId, level, cacheHints, arrayLoader);
		final VolatileImgCells<VolatileIntArray> cells = new VolatileImgCells<VolatileIntArray>(c, new Fraction(), dimensions, cellDimensions);
		final CachedCellImg<T, VolatileIntArray> img = new CachedCellImg<T, VolatileIntArray>(cells);
		return img;
	}

	@Override
	public VolatileGlobalCellCache getCache() {
		return cache;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms() {
		return mipmapTransforms;
	}
}
