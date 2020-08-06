package bdv.render;

import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.janelia.alignment.ArgbRenderer;
import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.util.ImageProcessorCache;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

public final class Rest {

	public static final String ownerFormat = "%s/owner/%s";
	public static final String stackListFormat = ownerFormat + "/stacks";
	public static final String stackFormat = ownerFormat + "/project/%s/stack/%s";
	public static final String stackBoundsFormat = stackFormat  + "/bounds";
	public static final String stackResolutionFormat = stackFormat  + "/resolutionValues";
	public static final String boundingBoxFormat = stackFormat + "/z/%d/box/%d,%d,%d,%d,%f";
	public static final String renderParametersFormat = boundingBoxFormat + "/render-parameters";
	public static final String pngTileFormat = stackFormat + "/tile/%s/png-image?scale=%s";
	public static final String jpegTileFormat = stackFormat + "/tile/%s/jpeg-image?scale=%s";
	public static final String pngMaskFormat = stackFormat + "/tile/%s/mask/png-image?scale=%s";

	private Rest() {}

	public static List<StackInfo> listStackInfos(
			final Gson gson,
			final String baseUrl,
			final String owner) {

		final String query = String.format(stackListFormat, baseUrl, owner);

		try (final InputStreamReader reader = new InputStreamReader(new URL(query).openStream())) {
			return gson.fromJson(reader, new TypeToken<ArrayList<StackInfo>>(){}.getType());
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return null;
		}
	}

	public static StackInfo getStackInfo(
			final Gson gson,
			final String baseUrl,
			final String owner,
			final String project,
			final String stack) {

		final String query = String.format(
				stackFormat,
				baseUrl,
				owner,
				project,
				stack);

		try (final InputStreamReader reader = new InputStreamReader(new URL(query).openStream())) {
			return gson.fromJson(reader, new TypeToken<StackInfo>(){}.getType());
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return null;
		}
	}

	public static Bounds getStackBounds(
			final Gson gson,
			final String baseUrl,
			final String owner,
			final String project,
			final String stack) {

		final String query = String.format(
				stackBoundsFormat,
				baseUrl,
				owner,
				project,
				stack);

		try (final InputStreamReader reader = new InputStreamReader(new URL(query).openStream())) {
			return gson.fromJson(reader, new TypeToken<Bounds>(){}.getType());
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return null;
		}
	}

	/**
	 * Get the resolution of a stack or default {1.0, 1.0, 1.0}.
	 *
	 * @param gson
	 * @param baseUrl
	 * @param owner
	 * @param project
	 * @param stack
	 * @return
	 */
	public static double[] getStackResolution(
			final Gson gson,
			final String baseUrl,
			final String owner,
			final String project,
			final String stack) {

		final String query = String.format(
				stackResolutionFormat,
				baseUrl,
				owner,
				project,
				stack);

		double[] resolution = null;
		try (final InputStreamReader reader = new InputStreamReader(new URL(query).openStream())) {
			resolution = gson.fromJson(reader, new TypeToken<double[]>(){}.getType());
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
		if (resolution == null)
			resolution = new double[]{1.0, 1.0, 1.0};

		return resolution;
	}

	public static final BufferedImage renderImage(
			final String baseUrl,
			final String owner,
			final String project,
			final String stack,
			final long x,
			final long y,
			final long z,
			final long w,
			final long h,
			final double scale,
			final boolean filter,
			final MipmapSourceRewriter rewriter) {

		final String renderParametersUrlString = String.format(
				renderParametersFormat,
				baseUrl,
				owner,
				project,
				stack,
				z,
				x,
				y,
				w,
				h,
				scale);

		final RenderParameters renderParameters = RenderParameters.loadFromUrl(renderParametersUrlString);
		renderParameters.setDoFilter(filter);

		rewriter.rewrite(renderParameters);

        final BufferedImage image = renderParameters.openTargetImage();
        ArgbRenderer.render(renderParameters, image, new ImageProcessorCache());

        return image;
	}
}
