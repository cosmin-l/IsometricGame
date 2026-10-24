package game;

import java.util.Random;

/** Generates every sound effect as a mono waveform from simple DSP building blocks. */
final class Synth {
    private static final int SR = Audio.SR;
    private static final double TAU = Math.PI * 2;
    private static final Random R = new Random(1234);

    private Synth() {}

    static float[] make(Audio.Sfx s) {
        float[] out = switch (s) {
            case SWING -> swing(0.2, 700, 2600, 0.55);
            case HIT -> hit(1.0);
            case CRIT -> mix(hit(1.0), bell(new double[]{880, 1320, 2210}, 0.4, 0.22));
            case BLOCK -> mix(noiseDecay(0.04, 0.7, 0.3, 0.5), bell(new double[]{520, 1230, 1870, 2650}, 0.5, 0.32));
            case MONSTER_DIE -> groan(0.6, 170, 65, 0.6);
            case BONE_DIE -> clatter(0.5, 10);
            case PLAYER_HURT -> groan(0.24, 240, 150, 0.55);
            case ALERT -> groan(0.45, 115, 92, 0.35);
            case FIREBALL -> mix(swing(0.45, 300, 1800, 0.5), rumble(0.45, 90, 50, 0.3));
            case EXPLOSION -> mix(noiseDecay(1.0, 0.3, 0.008, 0.9), rumble(0.8, 75, 28, 0.9));
            case FROST -> shimmer(0.8, 0.45);
            case ARROW -> mix(swing(0.14, 2000, 4200, 0.35), tone(330, 0.14, 0.22, 30));
            case GOLD -> coins();
            case PICKUP -> noiseDecay(0.1, 0.18, 0.04, 0.55);
            case POTION -> bubbles();
            case LEVEL_UP -> arpeggio(new double[]{293.7, 349.2, 440, 587.3, 880}, 0.11, 0.5);
            case PORTAL -> mix(sweep(1.1, 200, 900, 0.3), shimmer(1.1, 0.25));
            case STAIRS -> steps();
            case DEATH -> death();
            case CLICK -> tone(1400, 0.035, 0.22, 110);
            case BUTCHER -> groan(1.4, 95, 58, 0.95);
        };
        float peak = 0;
        for (float v : out) peak = Math.max(peak, Math.abs(v));
        if (peak > 0.8f) for (int i = 0; i < out.length; i++) out[i] *= 0.8f / peak; // keep headroom for the mixer
        int fade = Math.min(out.length, SR / 200); // de-click the tail
        for (int i = 0; i < fade; i++) out[out.length - 1 - i] *= i / (float) fade;
        return out;
    }

    private static float[] buf(double sec) { return new float[(int) (sec * SR) + 2]; }

    private static float[] mix(float[] a, float[] b) {
        float[] o = new float[Math.max(a.length, b.length)];
        for (int i = 0; i < a.length; i++) o[i] += a[i];
        for (int i = 0; i < b.length; i++) o[i] += b[i];
        return o;
    }

    private static float[] place(float[] dst, float[] src, double at, double gain) {
        int off = (int) (at * SR);
        if (off + src.length > dst.length) {
            float[] grown = new float[off + src.length];
            System.arraycopy(dst, 0, grown, 0, dst.length);
            dst = grown;
        }
        for (int i = 0; i < src.length; i++) dst[off + i] += (float) (src[i] * gain);
        return dst;
    }

    private static float noise() { return R.nextFloat() * 2 - 1; }

    /** Decaying sine. */
    private static float[] tone(double f, double dur, double amp, double decay) {
        float[] o = buf(dur);
        for (int i = 0; i < o.length; i++) {
            double t = i / (double) SR, atk = Math.min(1, t / 0.002);
            o[i] = (float) (Math.sin(TAU * f * t) * Math.exp(-t * decay) * amp * atk);
        }
        return o;
    }

    /** Noise through a band-pass filter sweeping from f0 to f1: whooshes. */
    private static float[] swing(double dur, double f0, double f1, double amp) {
        float[] o = buf(dur);
        double low = 0, band = 0;
        for (int i = 0; i < o.length; i++) {
            double t = i / (double) o.length, fc = f0 * Math.pow(f1 / f0, t);
            double f = 2 * Math.sin(Math.PI * fc / SR);
            low += f * band;
            double high = noise() - low - 0.6 * band;
            band += f * high;
            double env = Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.3)), 2);
            o[i] = (float) (band * env * amp);
        }
        return o;
    }

    /** Low-passed noise whose filter closes over time. */
    private static float[] noiseDecay(double dur, double c0, double c1, double amp) {
        float[] o = buf(dur);
        double lp = 0;
        for (int i = 0; i < o.length; i++) {
            double t = i / (double) o.length, c = c0 + (c1 - c0) * t;
            lp += c * (noise() - lp);
            o[i] = (float) (lp * Math.exp(-t * 5) * amp * Math.min(1, i / 40.0));
        }
        return o;
    }

    /** Sine with an exponential pitch drop: thumps and booms. */
    private static float[] rumble(double dur, double f0, double f1, double amp) {
        float[] o = buf(dur);
        double ph = 0;
        for (int i = 0; i < o.length; i++) {
            double t = i / (double) o.length;
            ph += TAU * f0 * Math.pow(f1 / f0, t) / SR;
            o[i] = (float) (Math.sin(ph) * Math.exp(-t * 4) * amp * Math.min(1, i / 60.0));
        }
        return o;
    }

    private static float[] hit(double amp) {
        return mix(rumble(0.15, 150, 55, 0.95 * amp), noiseDecay(0.07, 0.55, 0.1, 0.7 * amp));
    }

    /** Inharmonic partials with faster decay for higher ones: metal. */
    private static float[] bell(double[] freqs, double dur, double amp) {
        float[] o = buf(dur);
        for (int k = 0; k < freqs.length; k++) {
            double decay = (4 + k * 3) / dur, a = amp / (1 + k * 0.5);
            for (int i = 0; i < o.length; i++) {
                double t = i / (double) SR;
                o[i] += (float) (Math.sin(TAU * freqs[k] * t) * Math.exp(-t * decay) * a);
            }
        }
        return o;
    }

    /** Rough, falling sawtooth through a low-pass: grunts, groans and roars. */
    private static float[] groan(double dur, double f0, double f1, double amp) {
        float[] o = buf(dur);
        double ph = 0, lp = 0, lp2 = 0, rough = 0;
        for (int i = 0; i < o.length; i++) {
            double t = i / (double) o.length, sec = i / (double) SR;
            double f = f0 * Math.pow(f1 / f0, t) * (1 + 0.03 * Math.sin(TAU * 6 * sec));
            ph = (ph + f / SR) % 1;
            rough += 0.02 * (noise() - rough);
            double saw = (ph * 2 - 1) * (0.75 + rough * 3);
            lp += 0.09 * (saw - lp);
            lp2 += 0.09 * (lp - lp2);
            double env = Math.min(1, t * 12) * Math.pow(1 - t, 1.5);
            o[i] = (float) (lp2 * env * amp * 1.6);
        }
        return o;
    }

    /** Several dry clicks: bones falling apart. */
    private static float[] clatter(double dur, int n) {
        float[] o = buf(dur);
        for (int k = 0; k < n; k++) {
            double at = Math.pow(R.nextDouble(), 1.6) * (dur - 0.05);
            float[] click = mix(noiseDecay(0.02, 0.9, 0.4, 0.5), tone(700 + R.nextDouble() * 900, 0.04, 0.3, 120));
            o = place(o, click, at, 1 - at / dur * 0.6);
        }
        return o;
    }

    /** Sparkling high partials plus airy noise: ice and magic. */
    private static float[] shimmer(double dur, double amp) {
        float[] o = buf(dur);
        for (int k = 0; k < 10; k++) {
            double at = R.nextDouble() * dur * 0.5;
            o = place(o, tone(1800 + R.nextDouble() * 2600, dur - at, amp * 0.35, 6 + R.nextDouble() * 6), at, 1);
        }
        double lp = 0;
        for (int i = 0; i < o.length; i++) {
            double t = i / (double) o.length, n = noise();
            lp += 0.3 * (n - lp);
            o[i] += (float) ((n - lp) * amp * 0.25 * Math.exp(-t * 4));
        }
        return o;
    }

    private static float[] sweep(double dur, double f0, double f1, double amp) {
        float[] o = buf(dur);
        double ph = 0, ph2 = 0;
        for (int i = 0; i < o.length; i++) {
            double t = i / (double) o.length, sec = i / (double) SR;
            double f = f0 * Math.pow(f1 / f0, t) * (1 + 0.02 * Math.sin(TAU * 7 * sec));
            ph += TAU * f / SR;
            ph2 += TAU * f * 1.5 / SR;
            o[i] = (float) ((Math.sin(ph) + 0.5 * Math.sin(ph2)) * Math.sin(Math.PI * t) * amp);
        }
        return o;
    }

    private static float[] coins() {
        float[] o = buf(0.45);
        for (int k = 0; k < 3; k++) {
            double f = 2600 + R.nextDouble() * 1200;
            o = place(o, bell(new double[]{f, f * 1.48, f * 2.31}, 0.25, 0.25), k * 0.07 + R.nextDouble() * 0.02, 1 - k * 0.2);
        }
        return o;
    }

    private static float[] bubbles() {
        float[] o = buf(0.45);
        for (int k = 0; k < 4; k++) {
            float[] b = buf(0.06);
            double ph = 0, base = 260 + k * 70;
            for (int i = 0; i < b.length; i++) {
                double t = i / (double) b.length;
                ph += TAU * base * (1 + t * 1.2) / SR;
                b[i] = (float) (Math.sin(ph) * Math.sin(Math.PI * t) * 0.4);
            }
            o = place(o, b, k * 0.09, 1);
        }
        return o;
    }

    private static float[] arpeggio(double[] freqs, double stepSec, double amp) {
        float[] o = buf(freqs.length * stepSec + 1.0);
        for (int k = 0; k < freqs.length; k++) {
            double f = freqs[k];
            o = place(o, bell(new double[]{f, f * 2, f * 3.01}, 0.9, amp), k * stepSec, 1);
        }
        return o;
    }

    private static float[] steps() {
        float[] o = buf(0.9);
        for (int k = 0; k < 4; k++) {
            float[] thud = mix(rumble(0.12, 120, 70, 0.6), noiseDecay(0.06, 0.3, 0.05, 0.35));
            o = place(o, thud, k * 0.19, 1 - k * 0.2);
        }
        return o;
    }

    /** A slow, falling D-minor chord. */
    private static float[] death() {
        float[] o = buf(2.6);
        double[] fs = {73.42, 87.31, 110.0, 146.8};
        double[] ph = new double[fs.length];
        double lp = 0;
        for (int i = 0; i < o.length; i++) {
            double t = i / (double) o.length, s = 0;
            for (int k = 0; k < fs.length; k++) {
                ph[k] = (ph[k] + fs[k] * (1 - 0.12 * t) / SR) % 1;
                s += ph[k] * 2 - 1;
            }
            lp += 0.04 * (s - lp);
            o[i] = (float) (lp * Math.min(1, t * 6) * (1 - t) * 0.5);
        }
        return o;
    }
}
