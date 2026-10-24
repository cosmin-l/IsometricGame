package game;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Real-time software mixer: plays the synthesized sound effects and generates the music live.
 * Everything runs on one audio thread; the game only enqueues voices and sets the wanted theme.
 */
final class Audio {
    static final int SR = 44100;
    private static final int CHUNK = 1024;
    private static final float MUSIC_VOL = 0.8f, SFX_VOL = 0.8f;
    private static final Path SETTINGS = SaveGame.FILE.resolveSibling("settings.properties");

    enum Sfx {
        SWING, HIT, CRIT, BLOCK, MONSTER_DIE, BONE_DIE, PLAYER_HURT, ALERT, FIREBALL, EXPLOSION, FROST, ARROW,
        GOLD, PICKUP, POTION, LEVEL_UP, PORTAL, STAIRS, DEATH, CLICK, BUTCHER
    }

    enum Music { NONE, TOWN, DUNGEON }

    private static final class Voice {
        final float[] data;
        final int id;
        final double rate;
        final float gl, gr;
        double pos;

        Voice(float[] data, int id, double rate, float gl, float gr) {
            this.data = data;
            this.id = id;
            this.rate = rate;
            this.gl = gl;
            this.gr = gr;
        }
    }

    private final float[][] samples = new float[Sfx.values().length][];
    private final ConcurrentLinkedQueue<Voice> incoming = new ConcurrentLinkedQueue<>();
    private final List<Voice> voices = new ArrayList<>(); // audio thread only
    private final Random rnd = new Random();
    private volatile boolean running, musicOn = true, soundOn = true;
    private volatile Music wanted = Music.NONE;

    void start() {
        loadSettings();
        Thread t = new Thread(this::run, "audio");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    private void run() {
        SourceDataLine line;
        try {
            AudioFormat fmt = new AudioFormat(SR, 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, CHUNK * 4 * 4);
            line.start();
        } catch (Exception | LinkageError e) {
            System.err.println("Audio disabled: " + e);
            return;
        }
        running = true;
        for (Sfx s : Sfx.values()) samples[s.ordinal()] = Synth.make(s);

        MusicGen music = new MusicGen();
        float[] l = new float[CHUNK], r = new float[CHUNK];
        byte[] out = new byte[CHUNK * 4];
        int[] perId = new int[Sfx.values().length];
        while (running) {
            Arrays.fill(perId, 0);
            for (Voice v : voices) perId[v.id]++;
            for (Voice v; (v = incoming.poll()) != null; ) {
                if (perId[v.id] < 4 && voices.size() < 28) {
                    voices.add(v);
                    perId[v.id]++;
                }
            }
            Arrays.fill(l, 0);
            Arrays.fill(r, 0);
            for (Iterator<Voice> it = voices.iterator(); it.hasNext(); ) {
                Voice v = it.next();
                float[] d = v.data;
                for (int i = 0; i < CHUNK; i++) {
                    int p = (int) v.pos;
                    if (p + 1 >= d.length) break;
                    float frac = (float) (v.pos - p), s = d[p] + (d[p + 1] - d[p]) * frac;
                    l[i] += s * v.gl;
                    r[i] += s * v.gr;
                    v.pos += v.rate;
                }
                if (v.pos + 1 >= d.length) it.remove();
            }
            music.render(l, r, musicOn ? wanted : Music.NONE);
            for (int i = 0; i < CHUNK; i++) {
                short sl = (short) (softClip(l[i]) * 32000), sr = (short) (softClip(r[i]) * 32000);
                out[i * 4] = (byte) sl;
                out[i * 4 + 1] = (byte) (sl >> 8);
                out[i * 4 + 2] = (byte) sr;
                out[i * 4 + 3] = (byte) (sr >> 8);
            }
            line.write(out, 0, out.length);
        }
    }

    private static float softClip(float x) {
        x = Math.max(-1f, Math.min(1f, x));
        return 1.5f * x - 0.5f * x * x * x;
    }

    /** @param pan -1 (left) .. 1 (right) */
    void play(Sfx s, double vol, double pan) {
        if (!running || !soundOn || vol < 0.02 || incoming.size() > 32) return;
        float[] d = samples[s.ordinal()];
        if (d == null) return;
        boolean tonal = s == Sfx.LEVEL_UP || s == Sfx.CLICK || s == Sfx.DEATH;
        double rate = tonal ? 1 : 1 + (rnd.nextDouble() - 0.5) * 0.12;
        pan = Math.max(-1, Math.min(1, pan));
        double a = (pan + 1) * Math.PI / 4; // equal-power pan
        float v = (float) (vol * SFX_VOL * Math.sqrt(2));
        incoming.add(new Voice(d, s.ordinal(), rate, (float) Math.cos(a) * v, (float) Math.sin(a) * v));
    }

    void play(Sfx s) { play(s, 1, 0); }

    void setMusic(Music m) { wanted = m; }

    boolean musicOn() { return musicOn; }

    boolean soundOn() { return soundOn; }

    void toggleMusic() {
        musicOn = !musicOn;
        saveSettings();
    }

    void toggleSound() {
        soundOn = !soundOn;
        saveSettings();
    }

    private void loadSettings() {
        if (!Files.isRegularFile(SETTINGS)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(SETTINGS)) {
            p.load(r);
            musicOn = Boolean.parseBoolean(p.getProperty("music", "true"));
            soundOn = Boolean.parseBoolean(p.getProperty("sound", "true"));
        } catch (Exception ignored) {
            // defaults are fine
        }
    }

    private void saveSettings() {
        Properties p = new Properties();
        p.setProperty("music", String.valueOf(musicOn));
        p.setProperty("sound", String.valueOf(soundOn));
        try {
            Files.createDirectories(SETTINGS.getParent());
            try (Writer w = Files.newBufferedWriter(SETTINGS)) {
                p.store(w, "Depths of Tristfall settings");
            }
        } catch (Exception ignored) {
            // settings are a convenience; never interrupt the game for them
        }
    }

    // ------------------------------------------------------------------ music

    /** Karplus-Strong plucked string. */
    private static final class Pluck {
        final float[] buf;
        final float decay, gl, gr;
        int idx, life;

        Pluck(double freq, float amp, float pan, float decay, float bright, Random r) {
            buf = new float[Math.max(2, (int) Math.round(SR / freq))];
            float prev = 0;
            for (int k = 0; k < buf.length; k++) {
                prev += bright * ((r.nextFloat() * 2 - 1) - prev);
                buf[k] = prev * amp;
            }
            this.decay = decay;
            gl = 1 - Math.max(0, pan);
            gr = 1 + Math.min(0, pan);
            life = (int) (SR * 3.5);
        }

        float next() {
            int j = idx + 1 == buf.length ? 0 : idx + 1;
            float v = buf[idx];
            buf[idx] = (v + buf[j]) * 0.5f * decay;
            idx = j;
            life--;
            return v;
        }
    }

    private static double midi(int note) { return 440 * Math.pow(2, (note - 69) / 12.0); }

    /** Generative score for both themes, rendered sample by sample on the audio thread. */
    private static final class MusicGen {
        // Village: a 6/8 fingerpicked progression in D minor.
        private static final int[][] CHORDS_A = {{50, 57, 62, 65}, {48, 55, 60, 64}, {46, 53, 58, 62}, {45, 52, 57, 61}};
        private static final int[][] CHORDS_B = {{50, 57, 62, 65}, {43, 50, 55, 58}, {46, 53, 58, 65}, {45, 52, 57, 64}};
        private static final int[] PATTERN = {0, 1, 2, 3, 2, 1};
        private static final int[] D_MINOR = {62, 64, 65, 67, 69, 70, 72, 74, 76, 77};
        private static final int[] D_PHRYGIAN = {62, 63, 65, 67, 69, 70, 72, 74, 75, 77, 79};

        private final Random r = new Random();
        private final List<Pluck> plucks = new ArrayList<>();
        private final float[] dl = new float[(int) (SR * 0.43)], dr = new float[(int) (SR * 0.31)];
        private int il, ir, step, bar;
        private boolean altCycle;
        private Music current = Music.NONE;
        private float gain;
        private long t, nextEvent;
        private double ph1, ph2, ph3, lfo;
        private float wind;

        void render(float[] l, float[] rr, Music target) {
            for (int i = 0; i < l.length; i++) {
                if (current != target) {
                    gain -= 1f / (SR * 1.5f);
                    if (gain <= 0) {
                        gain = 0;
                        current = target;
                        plucks.clear();
                        step = bar = 0;
                        nextEvent = t + SR / 2;
                    }
                } else if (gain < 1) {
                    gain = Math.min(1, gain + 1f / (SR * 2.5f));
                }
                if (current != Music.NONE && t >= nextEvent) sequence();

                float sl = 0, sr = 0;
                for (int k = plucks.size() - 1; k >= 0; k--) {
                    Pluck p = plucks.get(k);
                    float v = p.next();
                    sl += v * p.gl;
                    sr += v * p.gr;
                    if (p.life <= 0) plucks.remove(k);
                }
                if (current == Music.DUNGEON) {
                    double tau = Math.PI * 2;
                    ph1 = (ph1 + tau * 73.42 / SR) % tau;
                    ph2 = (ph2 + tau * 110.3 / SR) % tau;
                    ph3 = (ph3 + tau * 36.71 / SR) % tau;
                    lfo = (lfo + tau * 0.06 / SR) % tau;
                    float swell = (float) (0.65 + 0.35 * Math.sin(lfo));
                    float drone = (float) (Math.sin(ph1) * 0.07 + Math.sin(ph2) * 0.035 + Math.sin(ph3) * 0.06) * swell;
                    wind += (0.015f + 0.012f * (float) Math.sin(lfo * 1.7)) * ((r.nextFloat() * 2 - 1) - wind);
                    sl += drone + wind * 0.10f;
                    sr += drone + wind * 0.10f;
                }

                // ping-pong echo for space
                float el = dl[il], er = dr[ir];
                dl[il] = sr * 0.5f + er * 0.42f;
                dr[ir] = sl * 0.5f + el * 0.42f;
                if (++il == dl.length) il = 0;
                if (++ir == dr.length) ir = 0;
                float echo = current == Music.DUNGEON ? 0.55f : 0.3f;
                l[i] += (sl + el * echo) * gain * MUSIC_VOL;
                rr[i] += (sr + er * echo) * gain * MUSIC_VOL;
                t++;
            }
        }

        private void add(Pluck p) {
            if (plucks.size() >= 16) plucks.remove(0);
            plucks.add(p);
        }

        private void sequence() {
            if (current == Music.TOWN) {
                int[] chord = (altCycle ? CHORDS_B : CHORDS_A)[bar % 4];
                boolean sparse = bar % 8 == 7;
                int s = step % 6;
                if (!sparse || s == 0) {
                    int note = chord[PATTERN[s]];
                    add(new Pluck(midi(note), s == 0 ? 0.32f : 0.22f, s == 0 ? -0.2f : (s % 2 == 0 ? 0.3f : -0.3f),
                            0.996f, 0.55f, r));
                }
                if ((s == 0 || s == 3) && r.nextDouble() < (sparse ? 0.2 : 0.55)) {
                    int note = r.nextDouble() < 0.6 ? chord[2 + r.nextInt(2)] + 12 : D_MINOR[r.nextInt(D_MINOR.length)] + 12;
                    add(new Pluck(midi(note), 0.17f, 0.15f, 0.997f, 0.8f, r));
                }
                if (++step % 6 == 0) {
                    bar++;
                    if (bar % 4 == 0) altCycle = r.nextDouble() < 0.45;
                }
                nextEvent = t + (long) (SR * 0.27);
            } else {
                double roll = r.nextDouble();
                if (roll < 0.09) {
                    add(new Pluck(midi(26), 0.6f, 0f, 0.9985f, 0.25f, r)); // distant boom
                } else {
                    int note = D_PHRYGIAN[r.nextInt(D_PHRYGIAN.length)] - (r.nextBoolean() ? 12 : 0);
                    float pan = r.nextFloat() * 1.4f - 0.7f;
                    add(new Pluck(midi(note), 0.2f, pan, 0.9985f, 0.3f, r));
                    if (roll > 0.85) add(new Pluck(midi(note + 1), 0.12f, -pan, 0.9985f, 0.3f, r)); // dissonant rub
                }
                nextEvent = t + (long) (SR * (1.6 + r.nextDouble() * 3.0));
            }
        }
    }
}
