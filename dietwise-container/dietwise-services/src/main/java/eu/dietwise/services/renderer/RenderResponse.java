package eu.dietwise.services.renderer;

public record RenderResponse(String html, String finalUrl, Screenshot screenshot) {
}
