package game;

/** Conversions between world (tile) coordinates and isometric screen coordinates. */
public final class Iso {
    public static final int TW = 64, TH = 32;

    private Iso() {}

    public static double sx(double x, double y) { return (x - y) * TW / 2.0; }

    public static double sy(double x, double y) { return (x + y) * TH / 2.0; }

    public static double wx(double sx, double sy) { return (sx / (TW / 2.0) + sy / (TH / 2.0)) / 2.0; }

    public static double wy(double sx, double sy) { return (sy / (TH / 2.0) - sx / (TW / 2.0)) / 2.0; }
}
