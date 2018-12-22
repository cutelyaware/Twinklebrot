import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JPanel;


public class VCRControls extends JPanel {
    public interface VCRListener {
        public void functionChanged(VCRButton.STATE newState);
    }
    private Set<VCRListener> vcrlisteners = new HashSet<VCRListener>();
    public void addVCRListener(VCRListener l) {
        vcrlisteners.add(l);
    }
    public void removeVCRListener(VCRListener l) {
        vcrlisteners.remove(l);
    }

    public VCRControls() {
        super();
        final VCRButton playPause = new VCRButton(VCRButton.STATE.PLAYING);
        playPause.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                toggle(playPause);
            }
        });
        final VCRButton recordStop = new VCRButton(VCRButton.STATE.STOPPED);
        recordStop.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                toggle(recordStop);
            }
        });
        add(playPause);
        add(recordStop);
    }

    protected void fireStateChanged(VCRButton.STATE state) {
        int num = vcrlisteners.size();
        for(VCRListener l : vcrlisteners)
            l.functionChanged(state);
    }

    private void toggle(VCRButton b) {
        VCRButton.STATE newState = opposite(b.getState());
        b.setState(newState);
        updateToolTip(b);
        fireStateChanged(newState);
    }

    private void updateToolTip(VCRButton b) {
        VCRButton.STATE newState = opposite(b.getState());
        switch(newState) {
            case PAUSED:
                newState = VCRButton.STATE.PLAYING;
                break;
            case PLAYING:
                newState = VCRButton.STATE.PAUSED;
                break;
            case RECORDING:
                newState = VCRButton.STATE.STOPPED;
                break;
            case STOPPED:
                newState = VCRButton.STATE.RECORDING;
        }
    }

    private VCRButton.STATE opposite(VCRButton.STATE state) {
        switch(state) {
            case PAUSED:
                return VCRButton.STATE.PLAYING;
            case PLAYING:
                return VCRButton.STATE.PAUSED;
            case RECORDING:
                return VCRButton.STATE.STOPPED;
            case STOPPED:
                return VCRButton.STATE.RECORDING;
        }
        return null;
    }
} // end class VCRControls


class VCRButton extends JButton {
    public enum STATE {
        PLAYING, PAUSED, STOPPED, RECORDING;
    }
    private final static int SIZE = 26;
    private STATE state;

    public void setState(STATE state) {
        this.state = state;
        String tip = null;
        switch(state) {
            case PLAYING:
                tip = "Pause";
                break;
            case PAUSED:
                tip = "Play";
                break;
            case STOPPED:
                tip = "Start Recording";
                break;
            case RECORDING:
                tip = "Stop Recording";
                break;
        }
        this.setToolTipText(tip);
        repaint();
    }

    public STATE getState() {
        return state;
    }

    public VCRButton(STATE state) {
        setState(state);
        setPreferredSize(new Dimension(SIZE, SIZE));
    }

    @Override
    public void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        int min_d = w > h ? h : w;
        int min2 = min_d / 2;
        int pad = min_d / 5;
        int box_x = cx - min2 + pad;
        int box_y = cy - min2 + pad;
        int box_d = min_d - 2 * pad; // Maximum dimension of graphic area.
        super.paint(g);
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        switch(state) {
            case PAUSED:
                g.setColor(Color.GREEN);
                int[] xs = {box_x, box_x + box_d, box_x};
                int[] ys = {box_y, cy, h - box_y};
                g.fillPolygon(xs, ys, xs.length);
                break;
            case PLAYING:
                g.setColor(Color.YELLOW);
                int step = box_d / 3;
                g.fillRect(box_x, box_y, step, box_d);
                g.fillRect(box_x + 2 * step, box_y, step, box_d);
                break;
            case STOPPED:
                g.setColor(Color.RED);
                g.fillOval(box_x, box_y, box_d, box_d);
                break;
            case RECORDING:
                g.setColor(Color.RED);
                g.fillOval(box_x, box_y, box_d, box_d);
                g.setColor(Color.WHITE);
                int off = 5;
                g.fillRect(box_x + off, box_y + off, box_d - 2 * off, box_d - 2 * off);
                break;
        }
    }
} // end class VCRButton
