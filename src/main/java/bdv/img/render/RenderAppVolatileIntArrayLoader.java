package bdv.img.render;

import bdv.img.cache.CacheArrayLoader;
import bdv.img.render.RenderAppImageLoader.SliceLoader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;

public class RenderAppVolatileIntArrayLoader implements CacheArrayLoader<VolatileIntArray> {

	private VolatileIntArray theEmptyArray;

	final private SliceLoader sliceLoader;

	/**
	 * <p>
	 * Create a {@link CacheArrayLoader} for a TrakEM2 source. Tiles are
	 * addressed by their pixel position and dimension.
	 * </p>
	 *
	 * @param
	 * @param tileWidth
	 * @param tileHeight
	 */
	public RenderAppVolatileIntArrayLoader(
			final Parameters p,
			final SliceLoader sliceLoader,
			final int[] zScales) {

		theEmptyArray = new VolatileIntArray(1, false);
		this.sliceLoader = sliceLoader;
	}

	@Override
	public int getBytesPerElement() {
		return 4;
	}

	@Override
	public VolatileIntArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min) throws InterruptedException {

		final int[] data = new int[dimensions[0] * dimensions[1]];
		sliceLoader.loadSlice(level, data, min, dimensions);

		return new VolatileIntArray(data, true);
	}

	@Override
	public VolatileIntArray emptyArray(final int[] dimensions) {
		int numEntities = 1;
		for (int i = 0; i < dimensions.length; ++i)
			numEntities *= dimensions[i];
		if (theEmptyArray.getCurrentStorageArray().length < numEntities)
			theEmptyArray = new VolatileIntArray(numEntities, false);
		return theEmptyArray;
	}
}
