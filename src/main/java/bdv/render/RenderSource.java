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


import java.io.IOException;

import bdv.tools.transformation.TransformedSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.VolatileRandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class RenderSource {
	/**
	 * Creates a volatile multiscale {@link Source} for a render stack
	 */
	@SuppressWarnings("unchecked")
	public static Source<VolatileARGBType> getVolatileSource(
			final Parameters p,
			final String name,
			final long[][] dimensions,
			final double[][] scales,
			final int[] zScales,
			final long[] offset,
			final VoxelDimensions voxelDimensions,
			final SharedQueue sharedQueue) throws IOException {

		final RandomAccessibleIntervalMipmapSource<ARGBType> source =
				getRandomAccessibleIntervalMipmapSource(
						p,
						name,
						dimensions,
						scales,
						zScales,
						offset,
						voxelDimensions);
		final VolatileRandomAccessibleIntervalMipmapSource<ARGBType, VolatileARGBType> volatileSource = source.asVolatile(new VolatileARGBType(), sharedQueue);
		//final Source<VolatileARGBType> transformedVolatileSource = applyTransform(volatileSource);
		//return transformedVolatileSource;
		return volatileSource;
	}

	/**
	 * Creates a multiscale {@link Source} for a render stack.
	 */
	public static Source<ARGBType> getSource(
			final Parameters p,
			final String name,
			final long[][] dimensions,
			final double[][] scales,
			final int[] zScales,
			final long[] offset,
			final VoxelDimensions voxelDimensions) throws IOException {

		final RandomAccessibleIntervalMipmapSource<ARGBType> source =
				getRandomAccessibleIntervalMipmapSource(
						p,
						name,
						dimensions,
						scales,
						zScales,
						offset,
						voxelDimensions);
//		final Source<ARGBType> transformedSource = applyTransform(source);
//		return transformedSource;
		return source;
	}

	@SuppressWarnings("unchecked")
	private static RandomAccessibleIntervalMipmapSource<ARGBType> getRandomAccessibleIntervalMipmapSource(
			final Parameters p,
			final String name,
			final long[][] dimensions,
			final double[][] scales,
			final int[] zScales,
			final long[] offset,
			final VoxelDimensions voxelDimensions) throws IOException {

		final RandomAccessibleInterval<ARGBType>[] scaleLevelImgs = new RandomAccessibleInterval[scales.length];
		final int[] blockSize = new int[]{p.tileWidth, p.tileHeight, 1};
		for (int s = 0; s < scales.length; ++s) {

			final SliceLoader loader =
					new SliceLoader(
							p.baseUrl,
							p.owner,
							p.project,
							p.stack,
							p.averageZ,
							p.filter,
							offset,
							s,
							zScales[s],
							p.rewrite ?
									new MipmapSourceRenderRewriter(p.baseUrl, p.owner, p.project, p.stack) :
									new MipmapSourceRewriter(){});

			final CellGrid grid = new CellGrid(dimensions[s], blockSize);

			System.out.println(grid);

			final ARGBType type = new ARGBType();
			final Cache<Long, Cell<VolatileIntArray>> cache =
					new SoftRefLoaderCache<Long, Cell<VolatileIntArray>>()
					.withLoader(LoadedCellCacheLoader.get(grid, loader, type, AccessFlags.setOf(AccessFlags.VOLATILE)));

			scaleLevelImgs[s] =
					new CachedCellImg<ARGBType, VolatileIntArray>(
							grid,
							type,
							cache,
							ArrayDataAccessFactory.get(PrimitiveType.INT, AccessFlags.setOf(AccessFlags.VOLATILE)));
		}

		final RandomAccessibleIntervalMipmapSource<ARGBType> source = new RandomAccessibleIntervalMipmapSource<>(
				scaleLevelImgs,
				new ARGBType(),
				scales,
				voxelDimensions,
				name );

		return source;
	}

	private static < T > Source< T > applyTransform(
			final Source< T > source,
			final int channel ) throws IOException
	{
		final TransformedSource< T > transformedSource = new TransformedSource<>( source );

		// account for the pixel resolution
		if ( source.getVoxelDimensions() != null )
		{
			final AffineTransform3D voxelSizeTransform = new AffineTransform3D();
			final double[] normalizedVoxelSize = getNormalizedVoxelSize( source.getVoxelDimensions() );
			for ( int d = 0; d < voxelSizeTransform.numDimensions(); ++d )
				voxelSizeTransform.set( normalizedVoxelSize[ d ], d, d );
			transformedSource.setFixedTransform( voxelSizeTransform );
		}

		// prepend with the source transform
//		final AffineTransform3D metadataTransform = metadata.getAffineTransform( channel );
		final AffineTransform3D metadataTransform = new AffineTransform3D();
		if ( metadataTransform != null )
			transformedSource.setIncrementalTransform( metadataTransform );

		return transformedSource;
	}

	private static double[] getNormalizedVoxelSize( final VoxelDimensions voxelDimensions )
	{
		double minVoxelDim = Double.POSITIVE_INFINITY;
		for ( int d = 0; d < voxelDimensions.numDimensions(); ++d )
			minVoxelDim = Math.min( minVoxelDim, voxelDimensions.dimension( d ) );
		final double[] normalizedVoxelSize = new double[ voxelDimensions.numDimensions() ];
		for ( int d = 0; d < voxelDimensions.numDimensions(); ++d )
			normalizedVoxelSize[ d ] = voxelDimensions.dimension( d ) / minVoxelDim;
		return normalizedVoxelSize;
	}
}
