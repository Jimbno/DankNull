package yalter.mousetweaks.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt a GUI out of Mouse Tweaks' drag-fill and wheel-transfer handling.
 *
 * <p>
 * Bundled verbatim from upstream (and from Mouse Tweaks' own API) so the annotation resolves whether or not Mouse
 * Tweaks is installed - it is read reflectively by name, so a duplicate definition is harmless.
 * </p>
 *
 * <p>
 * This matters here: a /dank/null slot holds far more than a vanilla stack and
 * {@code ContainerDankNull.slotClick} deliberately handles only the pickup and quick-move click modes. Left
 * un-opted-out, Mouse Tweaks would synthesise drag and wheel clicks the container ignores, and the client would
 * show stacks that the server never moved.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface MouseTweaksIgnore {}
