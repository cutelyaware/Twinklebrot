import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.beadsproject.beads.core.AudioContext;
import net.beadsproject.beads.core.IOAudioFormat;
import net.beadsproject.beads.core.UGen;
import net.beadsproject.beads.data.Buffer;
import net.beadsproject.beads.ugens.WavePlayer;

/**
 * Manages a set of input WavePlayers that loop until removed.
 * 
 * @author Melinda Green
 */
public class AudioManager {
    private final AudioContext ac = new AudioContext();
    private Map<double[], MyUgen> points2ugen = new HashMap<double[], MyUgen>();
    private int audio_frame, audio_buff_num;

    public interface AudioOutListener {
        public void audioOut(float[] output);
    }
    private Set<AudioOutListener> listeners = new HashSet<AudioOutListener>();
    public void addAudioOutListener(AudioOutListener l) {
        listeners.add(l);
    }
    public void removeAudioOutListener(AudioOutListener l) {
        listeners.remove(l);
    }
    protected void fireAudioOut(float[] out) {
        for(AudioOutListener aol : listeners) {
            aol.audioOut(out);
        }
    }

    public AudioManager() {
//        ac.invokeAfterEveryFrame(new Bead() {
//            @Override
//            protected void messageReceived(Bead arg0) {
//                System.out.println("Audio frame " + audio_frame++);
//            }
//        });
        // Add an output watcher that feeds each sample to all audio listeners.
        ac.out.addDependent(new UGen(ac) {
            @Override
            public void calculateBuffer() {
//                System.out.println("Output buffer " + audio_buff_num);
                fireAudioOut(getOutput());
                audio_buff_num++;
            }
        });
    }

    public float[] getOutput() {
        return ac.out.getOutBuffer(0);
    }

    public void getFormat() {
        IOAudioFormat format = ac.getAudioFormat();
        return;
    }

    private class MyUgen extends WavePlayer {
        private BrotBuff bbuf;
        private double amplitude;
        public MyUgen(AudioContext ac, BrotBuff bbuf, double amplitude) {
            //super(ac, 200, Buffer.SINE);
            super(ac, 1, null); // For testing, use frequency 200, and Buffer.SINE
            this.bbuf = bbuf;
            this.amplitude = amplitude;
            setBuffer(bbuf);
        }
        @Override
        public void calculateBuffer() {
            bbuf.nextChunk();
            super.calculateBuffer();
        }
        public double getAmplitude() {
            return amplitude;
        }
    } // end class MyUgen.

    /**
     * Merge a new input buffer into the output stream.
     * 
     * @param points contains the data to be played of the form x0,y0, x1,y1, x2,y2, etc. Currently only plays the x values.
     * @param amplitude
     */
    public void addBuffer(double[] points, double amplitude) {
        MyUgen ugen = new MyUgen(ac, new BrotBuff(points), amplitude);
        points2ugen.put(points, ugen);
        ac.out.addInput(ugen);
    }

    public void removeBuffer(double[] points) {
        MyUgen ugen = points2ugen.remove(points);
        if(ugen == null)
            return;
        ac.out.removeAllConnections(ugen);
    }

    public double[] removeFirst() {
        double[] points = points2ugen.keySet().iterator().next();
        points2ugen.remove(points);
        return points;
    }

    public boolean containsBuffer(double[] points) {
        return points2ugen.containsKey(points);
    }

    public double[] findQuietest() {
        double[] candidate = null;
        double quietest = Double.MAX_VALUE;
        for(double[] points : points2ugen.keySet()) {
            MyUgen ugen = points2ugen.get(points);
            double candidate_amplitude = ugen.getAmplitude();
            if(candidate_amplitude < quietest) {
                candidate = points;
                quietest = candidate_amplitude;
            }
        }
        return candidate;
    }

    public double getAmplitude(double[] points) {
        MyUgen ugen = points2ugen.get(points);
        return ugen == null ? -1 : points2ugen.get(points).getAmplitude();
    }

    public void replaceQuietest(double[] quietest, double[] with, double amplitude) {
        MyUgen ugen = points2ugen.get(quietest);
        removeBuffer(quietest);
        addBuffer(with, amplitude);
    }
    public int size() {
        return points2ugen.size();
    }

    public void clear() {
        for(MyUgen m : points2ugen.values())
            ac.out.removeAllConnections(m);
        points2ugen.clear();
    }

    public void start() {
        ac.start();
    }

    public void stop() {
        ac.stop();
    }

    public class BrotBuff extends Buffer {
        private int cur = 0;
        private double[] packedPoints;
        public BrotBuff(double[] points) {
            super(ac.getBufferSize());
            packedPoints = points;
        }
        public void nextChunk() {
            int length = packedPoints.length / 2;
            int size = ac.getBufferSize();
            for(int i = 0; i < size; i++) {
                buf[i] = (float) packedPoints[2 * cur];
                cur = (cur + 1) % length;
            }
        }
    } // end class BrotBuff


} // end class AudioManager
