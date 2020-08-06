/**
 *
 */
package bdv.render;

import org.janelia.alignment.RenderParameters;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public interface MipmapSourceRewriter {

	public default void rewrite(final RenderParameters renderParameters) {}

}
