package bdv.ij;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import bdv.BigDataViewer;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.render.Parameters;
import bdv.img.render.RenderAppImageLoader;
import bdv.img.render.StackInfo;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.WrapBasicImgLoader;
import bdv.tools.brightness.SetupAssignments;
import bdv.viewer.ViewerOptions;
import bdv.viewer.VisibilityAndGrouping;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;

/**
 * ImageJ plugin to show a render DB stack in BigDataViewer.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class OpenRenderAppPlugIn implements PlugIn {

	final private Gson gson = new Gson();

	private static Parameters params = new Parameters();

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

			final GenericDialog gd2 = new GenericDialog("BigDataViewer Render App");

			gd2.addChoice("Stack : ", projectStacks, params.project + " / " + params.stack);
			gd2.addNumericField("Tile_width : ", params.tileWidth, 0);
			gd2.addNumericField("Tile_height : ", params.tileHeight, 0);
			gd2.addCheckbox("average_z_sections", params.averageZ);
			gd2.addCheckbox("apply_contrast_filter", params.filter);
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

			run(params.clone(), gson);
		}
		catch (final IOException e) { e.printStackTrace(); }
	}

	final static public void run(final Parameters p, final Gson gson) throws IOException {

		final double pd = 10;

		final RenderAppImageLoader imgLoader = new RenderAppImageLoader(p, gson, 0, pd, 4);

		// get calibration and image size
		final FinalDimensions size = new FinalDimensions(Intervals.dimensionsAsLongArray(imgLoader.getImage(0)));
		final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions("px", 1, 1, pd);

		final int numTimepoints = 1;
		final int numSetups = 1;

		// create setups from channels
		final HashMap<Integer, BasicViewSetup> setups = new HashMap<Integer, BasicViewSetup>(numSetups);
		for (int s = 0; s < numSetups; ++s) {
			final BasicViewSetup setup = new BasicViewSetup(s, String.format("channel %d", s + 1), size, voxelSize);
			setup.setAttribute(new Channel(s + 1));
			setups.put(s, setup);
		}

		// create timepoints
		final ArrayList<TimePoint> timepoints = new ArrayList<TimePoint>(numTimepoints);
		for (int t = 0; t < numTimepoints; ++t)
			timepoints.add(new TimePoint(t));
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal(new TimePoints(timepoints), setups, imgLoader, null);

		// create ViewRegistrations from the images calibration
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		// sourceTransform.set( 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0 );
		final ArrayList<ViewRegistration> registrations = new ArrayList<ViewRegistration>();
		for (int t = 0; t < numTimepoints; ++t)
			for (int s = 0; s < numSetups; ++s)
				registrations.add(new ViewRegistration(t, s, sourceTransform));

		final File basePath = new File(".");
		final SpimDataMinimal spimData = new SpimDataMinimal(basePath, seq, new ViewRegistrations(registrations));
		WrapBasicImgLoader.wrapImgLoaderIfNecessary(spimData);

		final BigDataViewer bdv = BigDataViewer.open(spimData, "BigDataViewer", new ProgressWriterIJ(), ViewerOptions.options());
		final SetupAssignments sa = bdv.getSetupAssignments();
		final VisibilityAndGrouping vg = bdv.getViewer().getVisibilityAndGrouping();

		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				1, 0, 0, -(size.dimension(0) - bdv.getViewerFrame().getWidth()) / 2,
				0, 1, 0, -(size.dimension(1) - bdv.getViewerFrame().getHeight()) / 2,
				0, 0, 1, -(size.dimension(2) / 2 * pd));
		System.out.println(transform);
		bdv.getViewer().setCurrentViewerTransform(transform);
	}
}
