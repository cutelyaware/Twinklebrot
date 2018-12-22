import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import net.beadsproject.beads.core.AudioUtils;

import org.monte.media.Format;
import org.monte.media.avi.AVIWriter;


public class Twinklebrot {
    // Perfect fit YouTube resolutions
//    private final static int WIDTH = 640, HEIGHT = 480; // 480p
    private final static int WIDTH = 1280, HEIGHT = 720; // 720p
//    private final static int WIDTH = 1920, 1024 = 720; // 1080p

    // Parameter names. Note: Their values are stored by these names in preferences via PropertyManager.
    final static String
        N_TRAJECTORIES_NAME = "Trajectories",
        MIN_ITERATIONS_NAME = "Min Iterations",
        MAX_ITERATIONS_NAME = "Max Iterations",
        NUM_SEGMENTS_NAME = "Trail Length",
        IN_MSET_NAME = "In M-Set",
        RENDERING_NAME = "Rendering",
        SCALE_NAME = "Scale",
        FPS_NAME = "Recording  FPS",
        AUDIO_TRACKS_NAME = "Audio Tracks",
        MIRRORING_NAME = "Mirror";
    // Default parameter values.
    final static int DEF_N_TRAJECTORIES = 300;
    final static int DEF_MAX_ITERATIONS = 300;
    final static int DEF_MIN_ITERATIONS = 10;
    final static int DEF_SEGMENTS = 20;
    final static boolean DEF_IN_MSET = false; // b-brot.
    final static float DEF_SCALE = .5f;
    final static boolean DEF_RENDERING = true;
    final static int DEF_FPS = 30; // Output video speed in frames per second.
    final static int DEF_AUDIO_TRACKS = 0;
    final static boolean DEF_MIRRORING = true;
    // Range limits.
    final static int MIN_TRAJECTORIES = 1;
    final static int MAX_TRAJECTORIES = 10000;
    final static int MIN_MAX = 2; // Smallest max value allowed.
    final static int MAX_MAX = 100000; // Largest max value allowed.
    final static int MIN_MIN = 2; // Smallest min value allowed.
    final static int MAX_MIN = MAX_MAX; // Largest min value allowed.
    final static int MAX_ITERATIONS = DEF_N_TRAJECTORIES * 3; // M-Brot bailout threshold.
    final static int MIN_ITERATIONS = DEF_MIN_ITERATIONS; // M-Brot bailout threshold.
    final static float MIN_SCALE = .5f;
    final static float MAX_SCALE = 10f;
    final static boolean RANDOM_START = true;
    final static int MIN_FPS = 1;
    final static int MAX_FPS = 100;
    final static int MIN_AUDIO_TRACKS = 0;
    final static int MAX_AUDIO_TRACKS = 10;
    // Internal constants.
    private final static Deque<Trajectory> trajectories = new ArrayDeque<Trajectory>();
    final static Color
        BG = new Color(0, 0, 0),
        OTHER = new Color(1f, 1f, 1f),
        PLAYING = new Color(128, 255, 0),
        ANNOTATION = new Color(0f, .5f, 0f);
    private final static Color[] BULB_COLORS = {
        new Color(1f, .5f, 0f), // 1 - Main Cartoid
        new Color(.8f, 0, 0), // 2 - Head
        new Color(.5f, .5f, 1f), // 3 - Hands
        new Color(1f, 1f, 0f), // 4 - Top Knot
    };
    private final static double MIN_AUDIO_VOLUME = .5;

    // Graphic frames will be painted into this image. 
    // NOTE: This object will also be shared with the UI and audio components,
    // all of which must synchronize on it to avoid interfering with each other.
    private static BufferedImage syncThisImage = null;
    // A back buffer that maintains the most up-to-date complete frame.
    // Threads that modify it should hold the lock for the above image.
    private static BufferedImage backBuffer;
    private static Graphics2D G = null;
    static {
        initImage();
    }
    private static TwinkleUI UI = new TwinkleUI();
    private static AVIWriter aviWriter = null;


    static void initImage() {
        BufferedImage old = syncThisImage;
        syncThisImage = MonteImages.createImage(WIDTH, HEIGHT, (int) PropertyManager.getFloat(FPS_NAME, DEF_FPS));
        backBuffer = MonteImages.createImage(WIDTH, HEIGHT, (int) PropertyManager.getFloat(FPS_NAME, DEF_FPS));
        G = syncThisImage.createGraphics();
        G.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        G.drawImage(old, 0, 0, null);
    }

    ///AUDIO TRACK ENCODING
    // See http://www.randelshofer.ch/monte/javadoc/org/monte/media/avi/AVIOutputStream.html#addAudioTrack%28int,%20long,%20long,%20int,%20int,%20boolean,%20int,%20int%29
    private static int AudioTrackID;
    private static final int AudioWaveFormatTag = 0x0001; // Compression format of the audio stream given in MMREG.H, for example 0x0001 for WAVE_FORMAT_PCM.
    private static final long AudioRate = 44100; // Standard audio samples/second.
    private static final long AudioScale = 1; // Rate/Scale = samples per second.
    private static final int AudioNumberOfChannels = 1; // The number of channels: 1 for mono, 2 for stereo.
    private static final int AudioSampleSizeInBits = 16; // The number of bits in a sample: 8 or 16.
    private static final boolean AudioIsCompressed = false; // UNUSED! Whether the sound is compressed.
    private static final int AudioFrameDuration = 1; // UNUSED! The frame duration, expressed in the media's timescale, where the timescale is equal to the sample rate. For uncompressed formats, this field is always 1.
    private static final int AudioFrameSize = AudioSampleSizeInBits / 8; // For uncompressed audio, the number of bytes in a sample for a single channel (sampleSize divided by 8). For compressed audio, the number of bytes in a frame.
    private static final boolean BigEndian = true;

    ///VIDEO TRACK ENCODING
    // 
    private static int VideoTrackID;
    private static final String VideoFCCHandler = "tscc"; // Found from a breakpoint in simple version of AVIWriter.addTrack(Format)
    private static final long VideoRate = 30;
    private static final long VideoScale = 1; // Rate/Scale = frames/second?
    private static final int VideoWidth = WIDTH;
    private static final int VideoHeight = HEIGHT;
    private static final int VideoDepth = 24;
    private static final int VideoSyncInterval = 1; // 0=automatic. 1=all frames are keyframes. Values larger than 1 specify that for every n-th frame is a keyframe. 

    private static final double AudioSamplesPerVideoFrame = AudioRate / VideoRate;
    private static int AudioSamplesWritten = 0;

    static void beginWriting() {
        try {
            initImage();
            File file = new File("twinklebrot.avi");
            // Create the writer
            aviWriter = new AVIWriter(file);
            int fps = (int) PropertyManager.getFloat(FPS_NAME, DEF_FPS);
            // Create a video track.
            Format format = MonteImages.createFormat(VideoWidth, VideoHeight, (int) VideoRate);
            VideoTrackID = aviWriter.addTrack(format);
            VideoTrackID = aviWriter.addVideoTrack(
                VideoFCCHandler, // fccHandler 
                VideoScale, // scale
                VideoRate, // rate 
                VideoWidth, // width 
                VideoHeight, // height 
                VideoDepth, // depth 
                VideoSyncInterval // syncInterval
            );
            aviWriter.setPalette(VideoTrackID, syncThisImage.getColorModel());
            // Create an audio track.
            AudioTrackID = aviWriter.addAudioTrack(
                AudioWaveFormatTag,
                AudioScale, // Scale
                AudioRate, // Rate (Rate/Scale = samples per second)
                AudioNumberOfChannels,
                AudioSampleSizeInBits,
                AudioIsCompressed,
                AudioFrameDuration,
                AudioFrameSize);
            return;
        } catch(IOException e) {
            e.printStackTrace();
            aviWriter = null;
        }
    } // end beginWriting()

    static void endWriting() {
        synchronized(syncThisImage) {
            if(aviWriter != null) {
                try {
                    aviWriter.close();
                } catch(Exception e) {
                    System.out.println("Close failed");
                    e.printStackTrace();
                }
                aviWriter = null;
            }
        }
    } // end endWriting()

    // Cached m-brot/b-brot mode used in last frame so renderer can notice when it's changed.
    private static boolean last_escaper_mode = DEF_IN_MSET;
    private static int videoFrameNumber = 0;

    public static void main(String[] args) throws Exception, ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {
        boolean laf_set = false;
        for(LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                    laf_set = true;
                } catch(Exception e) {}
                break;
            }
        }
        if(!laf_set) {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        AudioManager audio = new AudioManager();
        audio.getFormat();
        // Attach an audio listener that will write audio frames to the AVI writer while recording.
        audio.addAudioOutListener(new AudioManager.AudioOutListener() {
            //int lastVideoFrame = -1;
            @Override
            public void audioOut(float[] output) {
                if(aviWriter != null) {
                    synchronized(syncThisImage) {
                        try {
                            writeAudio(output);
                        } catch(IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        audio.start();
        try {
            // initialize the animation
            G.setBackground(BG);
            G.setColor(OTHER);
            G.clearRect(0, 0, syncThisImage.getWidth(), syncThisImage.getHeight());
            int iw = syncThisImage.getWidth();
            int ih = syncThisImage.getHeight();
            int cw = iw / 2;
            int ch = ih / 2;
            // Draw all trajectories, then remove the finished ones.
            // We can't remove elements of a collection while iterating over it 
            // so we mark the undesired ones as we find them and remove them all at the end.
            Set<Trajectory> to_remove = new HashSet<Trajectory>();
            while(true) {
                // Create an animation frame
                G.clearRect(0, 0, syncThisImage.getWidth(), syncThisImage.getHeight());
                // Remove finished trajectories and adjust number to match user's desired number.
                int target_trajectories = (int) PropertyManager.getFloat(N_TRAJECTORIES_NAME, DEF_N_TRAJECTORIES);
                int low = (int) PropertyManager.getFloat(MIN_ITERATIONS_NAME, DEF_MIN_ITERATIONS);
                int high = (int) PropertyManager.getFloat(MAX_ITERATIONS_NAME, DEF_MAX_ITERATIONS);
                boolean in_mset_only = PropertyManager.getBoolean(IN_MSET_NAME, DEF_IN_MSET);
                if(in_mset_only != last_escaper_mode) { // Mode changed by user.
                    last_escaper_mode = in_mset_only;
                    trajectories.clear(); // Start with clean slate.
                    audio.clear();
                }
                for(Trajectory t : trajectories) {
                    int bulb = Trajectory.bulbPeriod(t.getCr(), t.getCi());
                    if(bulb > 0)
                        G.setColor(BULB_COLORS[bulb - 1]);
                    else
                        G.setColor(OTHER);
                    if(audio.containsBuffer(t.getPackedPoints()))
                        G.setColor(PLAYING);
                    if(!in_mset_only || PropertyManager.getBoolean(BulbControls.PREFIX + bulb, true))
                        drawSegments(G, t.getPackedPoints(), t.getStart(),
                            (int) PropertyManager.getFloat(NUM_SEGMENTS_NAME, DEF_SEGMENTS),
                            ch, cw, // Note: Swapping width & height to draw with the "head" at top.
                            PropertyManager.getFloat(SCALE_NAME, DEF_SCALE) * WIDTH,
                            PropertyManager.getBoolean(MIRRORING_NAME, DEF_MIRRORING));
                    t.advance();
                    if(!t.canAdvance() || t.getStart() >= t.length()) { // Remove finished trajectories.
                        to_remove.add(t);
                    }
                    else if(low > t.length() || t.length() > high)
                        to_remove.add(t); // User changed the limits and this trajectory is no longer desired.
                    else if(in_mset_only && !PropertyManager.getBoolean(BulbControls.PREFIX + bulb, true))
                        to_remove.add(t);
                }
                int audio_target = (int) PropertyManager.getFloat(AUDIO_TRACKS_NAME, DEF_AUDIO_TRACKS);
                // Too many current audio clips playing? These should be the first to go.
                while(audio.size() > audio_target) {
                    double[] points = audio.removeFirst();
                    for(Trajectory t : trajectories) {
                        if(t.getPackedPoints() == points) {
                            to_remove.add(t);
                            continue;
                        }
                    }
                }
                trajectories.removeAll(to_remove);
                // Still too many? Remove more.
                while(trajectories.size() > target_trajectories)
                    to_remove.add(trajectories.remove()); // Remove oldest first.
                // Remove any audio tracks associated with the trajectories being removed.
                for(Trajectory t : to_remove) {
                    audio.removeBuffer(t.getPackedPoints());
                }
                // Not enough trajectories to draw? Add new ones.
                while(trajectories.size() < target_trajectories && low < high) {
                    Trajectory t = Trajectory.makeTrajectory(low, high, in_mset_only, RANDOM_START);
                    trajectories.add(t);
                    double guess = t.guessAmplitude();
                    double[] packedPoints = t.getPackedPoints();
                    boolean loud_enough = guess > MIN_AUDIO_VOLUME;
                    if(audio.size() < audio_target) {
                        if(loud_enough)
                            audio.addBuffer(packedPoints, guess);
                    } else if(loud_enough) {
                        // We have enough audio trajectories, but have an opportunity in this case
                        // to replace a quiet trajectory with a louder one.
                        // See if we can find a quiet one.
                        double[] quietest = audio.findQuietest();
                        if(guess > audio.getAmplitude(quietest))
                            audio.replaceQuietest(quietest, packedPoints, guess);
                    }
                }
                // Not enough trajectories playing audio? Flag more existing ones.
                for(Iterator<Trajectory> it = trajectories.iterator(); it.hasNext() && audio.size() < audio_target;) {
                    Trajectory t = it.next();
                    if(!audio.containsBuffer(t.getPackedPoints()) && t.guessAmplitude() > MIN_AUDIO_VOLUME) {
                        audio.addBuffer(t.getPackedPoints(), t.guessAmplitude());
                    }
                }
                // *Still* not enough audio tracks? Make one more pass and add the loudest non-playing trajectory.
                // Note: this will only convert one trajectory per pass through the main loop.
                if(audio.size() < audio_target) {
                    Trajectory loudest_non_playing = null;
                    for(Iterator<Trajectory> it = trajectories.iterator(); it.hasNext() && audio.size() < audio_target;) {
                        Trajectory t = it.next();
                        if(!audio.containsBuffer(t.getPackedPoints())) {
                            if(loudest_non_playing == null || t.guessAmplitude() > loudest_non_playing.guessAmplitude())
                                loudest_non_playing = t;
                        }
                    }
                    if(loudest_non_playing != null)
                        audio.addBuffer(loudest_non_playing.getPackedPoints(), loudest_non_playing.guessAmplitude());
                }
                //System.out.println("playing " + audio.size() + " of " + trajectories.size() + " trajectories.");
                syncThisImage.flush();
                synchronized(syncThisImage) { // Block until UI gets a chance to paint the frame.
                    writeVideo();
                    try {
                        syncThisImage.wait();
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch(OutOfMemoryError oome) {
            JOptionPane.showMessageDialog(null, "Out Of Memory! Resetting your maximum trajectories. Please restart.");
            PropertyManager.userprefs.setProperty(N_TRAJECTORIES_NAME, "" + DEF_N_TRAJECTORIES);
            System.exit(1);
        } catch(IllegalMonitorStateException imse) {
            imse.printStackTrace();
        } finally {
            // Cancel all audio
            audio.stop();
            // Close the writer
            if(aviWriter != null) {
                aviWriter.close();
            }
            // Dispose the graphics object
            G.dispose();
        }
    } // end main()

    private static void writeVideo() {
        backBuffer.setData(syncThisImage.getData());
        UI.setImage(syncThisImage); // Updates the program icon to show the current image.
        if(aviWriter == null)
            return;

        double d_frames_needed = AudioSamplesWritten / AudioSamplesPerVideoFrame - videoFrameNumber;
        //System.out.println("writeVideo() Frames needed = " + d_frames_needed);
        int i_frames_needed = (int) d_frames_needed;
        i_frames_needed = 1;
        try {
            for(int f = 0; f < i_frames_needed; f++) { /////WRITE VIDEO
                aviWriter.write(VideoTrackID, backBuffer, 1); // write it to the writer (duration UNUSED!)
                videoFrameNumber++;
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
    private static int lastVideoFrame = -1;

    private static void writeAudio(float[] output) throws IOException {
        if(aviWriter == null)
            return; // AVI writing ended before we got the lock.
        byte[] encoded = new byte[output.length * 2];
        AudioUtils.floatToByte(encoded, output, BigEndian);
        swapBytes(encoded); // Seems to be required. Don't know why.
        boolean is_keyframe = lastVideoFrame != videoFrameNumber;
        is_keyframe = true; // TEST
        lastVideoFrame = videoFrameNumber;
        try { /////WRITE AUDIO
            aviWriter.writeSamples(
                AudioTrackID,
                output.length, // sampleCount
                encoded, // data
                0, // offset 
                encoded.length, // total bytes 
                is_keyframe);
        } catch(IOException e) {
            e.printStackTrace();
        }
        AudioSamplesWritten += output.length;
    } // end writeAudio()

    @SuppressWarnings("unused")
    private static int count(Trajectory[] trajectories, int min, int max) {
        int n = 0;
        for(Trajectory t : trajectories) {
            if(t.length() >= min && t.length() < max)
                n++;
        }
        return n;
    }

    // Scratch variables.
    private final static int[] xs = new int[MAX_MAX];
    private final static int[] ys = new int[MAX_MAX];

    static void drawSegments(Graphics2D g, double[] path, int start, int segments, int xoff, int yoff, double scale, boolean mirror) {
        int total_path_points = path.length / 2 - 1;
        int max_points_to_draw = segments + 1;
        int n_points_to_draw = 0;
        float[] color = g.getColor().getColorComponents(null);
        int width = syncThisImage.getWidth();
        for(int s = 0; s < max_points_to_draw; s++) { //&& s < 2 * (cur + s)
            int poff = start + s - segments + 1; // Offset of point within path. +1 to cur with first visible packedPoints at path head.
            if(0 > poff)
                continue; // TODO truncate aviWriter of range packedPoints rather than looping over them.
            if(poff >= total_path_points)
                break;
            try {
                double x = path[2 * (poff) + 0] + .5; // The .5 centers the image at the neck.
                double y = path[2 * (poff) + 1];
                xs[n_points_to_draw] = xoff + (int) (x * scale);
                ys[n_points_to_draw] = yoff + (int) (y * scale);
                n_points_to_draw++;
                if(n_points_to_draw > 1) {
                    float alpha = (n_points_to_draw - 1f) / max_points_to_draw;
                    int x1 = xs[n_points_to_draw - 1];
                    int y1 = ys[n_points_to_draw - 1];
                    int x2 = xs[n_points_to_draw - 2];
                    int y2 = ys[n_points_to_draw - 2];
                    G.setColor(new Color(color[0], color[1], color[2], alpha / 8));
                    G.drawLine(y2, x2, y1, x1);
                    if(mirror)
                        G.drawLine(width - y2, x2, width - y1, x1);
                }
            } catch(ArrayIndexOutOfBoundsException e) {
                System.err.println("Array bounds exception");
                return;
            }
        }
    } // end drawSegments()

    public static void swapBytes(byte[] bytes) {
        for(int i = 0; i < bytes.length / 2; i++) {
            byte a = bytes[2 * i];
            bytes[2 * i] = bytes[2 * i + 1];
            bytes[2 * i + 1] = a;
        }
    }

    public static byte[] long2bytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }
    public static byte[] float2bytes(float value) {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }
    public static byte[] floats2bytes(float[] values) {
        byte[] encoded = new byte[4 * values.length];
        for(int i = 0; i < values.length; i++) {
//            ByteBuffer.allocate(4).putFloat(values[i]).get(encoded, 4 * i, 4);
            byte[] four = float2bytes(values[i]);
//            byte[] four = ByteBuffer.allocate(4).putFloat(values[i]).array();
            System.arraycopy(four, 0, encoded, 4 * i, 4);
        }
        return encoded;
    }
    public static byte[] roundFloats(float[] values) {
        byte[] encoded = new byte[values.length];
        for(int i = 0; i < values.length; i++)
            encoded[i] = (byte) values[i];
        return encoded;
    }
}
