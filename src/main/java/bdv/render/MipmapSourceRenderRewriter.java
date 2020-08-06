/**
 *
 */
package bdv.render;

import java.util.Map.Entry;

import org.janelia.alignment.ImageAndMask;
import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.spec.ChannelSpec;
import org.janelia.alignment.spec.TileSpec;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class MipmapSourceRenderRewriter implements MipmapSourceRewriter {

	protected final String tileUrlFormatString;
	protected final String maskUrlFormatString;

	public MipmapSourceRenderRewriter(
			final String baseUrl,
			final String owner,
			final String project,
			final String stack) {

		tileUrlFormatString = String.format(Rest.pngTileFormat, baseUrl, owner, project, stack, "%s", "%f");
		maskUrlFormatString = String.format(Rest.pngMaskFormat, baseUrl, owner, project, stack, "%s", "%f");
	}

	@Override
	public void rewrite(final RenderParameters renderParameters) {

		for (final TileSpec tileSpec : renderParameters.getTileSpecs()) {

			for (ChannelSpec channel : tileSpec.getAllChannels()) {

				for (Entry<Integer, ImageAndMask> mipmapLevel : channel.getMipmapLevels().entrySet()) {

					final double scale = 1.0 / (1 << mipmapLevel.getKey());

					channel.putMipmap(
							mipmapLevel.getKey(),
							new ImageAndMask(
									String.format(tileUrlFormatString, tileSpec.getTileId(), scale),
									mipmapLevel.getValue().hasMask() ? String.format(maskUrlFormatString, tileSpec.getTileId(), scale): null));
				}
			}
		}
	}
}