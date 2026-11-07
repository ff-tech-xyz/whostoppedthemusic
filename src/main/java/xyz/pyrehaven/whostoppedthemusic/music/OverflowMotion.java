package xyz.pyrehaven.whostoppedthemusic.music;

/** One hover clock per line; leaving, fitting or changing content resets it. */
public final class OverflowMotion {
    private String content;
    private long started;
    private boolean hovering;

    public double offset(String content, double overflow, long now, boolean hovered) {
        if (!hovered || overflow <= 0 || !hovering || !content.equals(this.content)) {
            this.content = content;
            started = now;
        }
        hovering = hovered && overflow > 0;
        return hovering ? offset(overflow, now - started) : 0;
    }

    public static double offset(double overflow, long elapsedMillis) {
        if (overflow <= 0) return 0;
        double travel = overflow / 20.0;
        double phase = Math.max(0, elapsedMillis) / 1000.0 % (travel * 2 + 2);
        if (phase <= 1) return 0;
        if (phase <= 1 + travel) return (phase - 1) * 20;
        if (phase <= 2 + travel) return overflow;
        return overflow - (phase - 2 - travel) * 20;
    }
}
