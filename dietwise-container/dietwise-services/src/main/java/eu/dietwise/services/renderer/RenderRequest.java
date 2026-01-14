package eu.dietwise.services.renderer;

import eu.dietwise.v1.types.Viewport;

public record RenderRequest(String url, Boolean simplify, Integer timeout, Viewport viewport, Boolean includeScreenshot) {
}
