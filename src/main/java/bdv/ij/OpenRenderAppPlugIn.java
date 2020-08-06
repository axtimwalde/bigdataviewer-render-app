package bdv.ij;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import bdv.render.Bounds;
import bdv.render.Parameters;
import bdv.render.RenderSource;
import bdv.render.Rest;
import bdv.render.StackInfo;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;

/**
 * ImageJ plugin to show a render DB stack in BigDataViewer.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class OpenRenderAppPlugIn implements PlugIn {

	final private Gson gson = new Gson();

	private static Parameters params = new Parameters();

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

	public static void main(final String... args) {

		final ImageJ ij = new ImageJ();

		new OpenRenderAppPlugIn().run("");
	}

	@Override
	public void run(final String arg) {

		final GenericDialog gd = new GenericDialog("BigDataViewer Render App");
		gd.addStringField("Base_URL : ", params.baseUrl, params.baseUrl.length());
		gd.addStringField("Owner : ", params.owner);
		gd.showDialog();

		if (gd.wasCanceled())
			return;

		params.baseUrl = gd.getNextString();
		params.owner = gd.getNextString();

		final String stackListQuery = String.format(
				Parameters.stackListFormat,
				params.baseUrl,
				params.owner);

		try (final InputStreamReader reader = new InputStreamReader(new URL(stackListQuery).openStream())) {

			@SuppressWarnings("serial")
			final ArrayList<StackInfo> stackInfos = gson.fromJson(reader, new TypeToken<ArrayList<StackInfo>>(){}.getType());

			final String[] projects = new String[stackInfos.size()];
			final String[] stacks = new String[stackInfos.size()];
			final String[] projectStacks = new String[stackInfos.size()];
			for (int i = 0; i < stackInfos.size(); ++i) {
				final StackInfo stackInfo = stackInfos.get(i);
				projects[i] = stackInfo.stackId.project;
				stacks[i] = stackInfo.stackId.stack;
				projectStacks[i] = stackInfo.stackId.project + " / " + stackInfo.stackId.stack;
			}

			final GenericDialog gd2 = new GenericDialog("RenderView");

			gd2.addChoice("Stack : ", projectStacks, params.project + " / " + params.stack);
			gd2.addNumericField("Tile_width : ", params.tileWidth, 0);
			gd2.addNumericField("Tile_height : ", params.tileHeight, 0);
			gd2.addCheckbox("average_z_sections", params.averageZ);
			gd2.addCheckbox("apply_contrast_filter", params.filter);
			gd2.addCheckbox("rewrite_mipmap_URLs", params.rewrite);
			gd2.showDialog();

			if (gd2.wasCanceled())
				return;

			final int stackIndex = gd2.getNextChoiceIndex();

			params.project = projects[ stackIndex ];
			params.stack = stacks[ stackIndex ];

			params.tileWidth = (int)gd2.getNextNumber();
			params.tileHeight = (int)gd2.getNextNumber();
			params.averageZ = gd2.getNextBoolean();
			params.filter = gd2.getNextBoolean();
			params.filter = gd2.getNextBoolean();

			run(params.clone(), gson);
		}
		catch (final IOException e) { e.printStackTrace(); }
	}

	final static public void run(final Parameters p, final Gson gson) throws IOException {

		final String displayName = String.format("RenderView %s %s", p.project, p.stack);

		final Bounds bounds = Rest.getStackBounds(gson, p.baseUrl, p.owner, p.project, p.stack);
		if (bounds == null) return;

		final double[] resolution = Rest.getStackResolution(gson, p.baseUrl, p.owner, p.project, p.stack);
		final double zScale = resolution[0] / resolution[2];

		final long[] offset = new long[] { (long) bounds.minX, (long) bounds.minY, (long) bounds.minZ };
		final FinalDimensions size =
				new FinalDimensions(
						new long[]{
								(long)Math.ceil(bounds.maxX - bounds.minX + 1),
								(long)Math.ceil(bounds.maxY - bounds.minY + 1),
								(long)Math.ceil(bounds.maxZ - bounds.minZ + 1)});;
		final int numScales = getNumScales(size.dimension(0), size.dimension(1), p.tileWidth, p.tileHeight);;

		final double[][] mipmapResolutions = new double[numScales][];
		final long[][] dimensions = new long[numScales][];
		final AffineTransform3D[] mipmapTransforms = new AffineTransform3D[numScales];
		final int[] zScales = new int[numScales];
		for (int l = 0; l < numScales; ++l) {

			final int sixy = 1 << l;
			final int siz = p.averageZ ? Math.max(1, (int)Math.round(sixy / zScale)) : 1;

			mipmapResolutions[l] = new double[] { sixy, sixy, siz };
			dimensions[l] = new long[] {
					Math.max(1, size.dimension(0) >> l),
					Math.max(1, size.dimension(1) >> l),
					Math.max(1, size.dimension(2) / siz)};
			zScales[l] = siz;

			final AffineTransform3D mipmapTransform = new AffineTransform3D();

			mipmapTransform.set(sixy, 0, 0);
			mipmapTransform.set(sixy, 1, 1);
			mipmapTransform.set(zScale * siz, 2, 2);

//			mipmapTransform.set(0.5 * (sixy - 1), 0, 3);
//			mipmapTransform.set(0.5 * (sixy - 1), 1, 3);
			mipmapTransform.set(0.5 * (zScale * siz - 1), 2, 3);

			mipmapTransforms[l] = mipmapTransform;
		}

		final BdvOptions bdvOptions = BdvOptions.options();
		bdvOptions.frameTitle(displayName);

		final SharedQueue sharedQueue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
		final FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions("nm", mipmapResolutions[0]);

		final Source<ARGBType> source = RenderSource.getSource(
				params,
				displayName,
				dimensions,
				mipmapResolutions,
				zScales,
				offset,
				voxelDimensions);
		final Source<VolatileARGBType> volatileSource = RenderSource.getVolatileSource(
				params,
				displayName,
				dimensions,
				mipmapResolutions,
				zScales,
				offset,
				voxelDimensions,
				sharedQueue);

//		ImageJFunctions.show(source.getSource(0, 3));

		// show in BDV
		final BdvStackSource<VolatileARGBType> stackSource = BdvFunctions.show(volatileSource, bdvOptions);
		stackSource.setDisplayRange(0, 255);

		// reuse BDV handle
		bdvOptions.addTo(stackSource.getBdvHandle());
	}
}
