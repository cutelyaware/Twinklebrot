import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * This is the UI for the Twinklebrot Explorer. A Twinklebrot is an animation
 * showing a set of Mandelbrot (or other IFS) trajectories in motion.
 * As a given trajectory finishes, it is replaced by a new one that meets the parameter constraints.
 * The UI displays a shared image that is continuously being updated in a background thread.
 * Parameter changes made by the user are communicated to that thread by storing
 * them in shared preferences.
 * 
 * @author Melinda Green
 */
@SuppressWarnings("serial")
public class TwinkleUI extends StaticUtils.QuickFrame {
    private final static String
        NON_ESCAPERS = "Non Escapers",
        ESCAPERS = "Escapers";
    private BufferedImage shared_image = null;

    final JPanel picturePanel = new JPanel() {
        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if(shared_image == null)
                return;
            // Draw the image in the largest, centered rectangle that fits in the picture panel.
            Rectangle2D viewport = calcViewport(shared_image.getWidth(), shared_image.getHeight(), getBounds());
            int vx = (int) Math.round(viewport.getX());
            int vy = (int) Math.round(viewport.getY());
            int vw = (int) Math.round(viewport.getWidth());
            int vh = (int) Math.round(viewport.getHeight());
            g.drawImage(shared_image, vx, vy, vw, vh, null);
            // Draw parameter strings in the upper left corner of the image.
            g.setColor(Twinklebrot.ANNOTATION);
            int xoff = vx + 6;
            int yoff = vy;
            g.drawString(PropertyManager.getBoolean(Twinklebrot.IN_MSET_NAME, Twinklebrot.DEF_IN_MSET) ? "Inside M-Set" : "Outside M-Set", xoff, yoff += 15);
            g.drawString(Twinklebrot.N_TRAJECTORIES_NAME + ": " + (int) PropertyManager.getFloat(Twinklebrot.N_TRAJECTORIES_NAME, Twinklebrot.DEF_N_TRAJECTORIES), xoff, yoff += 15);
            g.drawString(Twinklebrot.MAX_ITERATIONS_NAME + ": " + (int) PropertyManager.getFloat(Twinklebrot.MAX_ITERATIONS_NAME, Twinklebrot.DEF_MAX_ITERATIONS), xoff, yoff += 15);
            g.drawString(Twinklebrot.MIN_ITERATIONS_NAME + ": " + (int) PropertyManager.getFloat(Twinklebrot.MIN_ITERATIONS_NAME, Twinklebrot.DEF_MIN_ITERATIONS), xoff, yoff += 15);
            g.drawString(Twinklebrot.NUM_SEGMENTS_NAME + ": " + (int) PropertyManager.getFloat(Twinklebrot.NUM_SEGMENTS_NAME, Twinklebrot.DEF_SEGMENTS), xoff, yoff += 15);
            g.drawString(Twinklebrot.SCALE_NAME + ": " + ("" + PropertyManager.getFloat(Twinklebrot.SCALE_NAME, Twinklebrot.DEF_SCALE)).substring(0, 3), xoff, yoff += 15);
            synchronized(shared_image) {
                if(PropertyManager.getBoolean(Twinklebrot.RENDERING_NAME, Twinklebrot.DEF_RENDERING))
                    shared_image.notify(); // Free the background thread to modify the shared_image.
            }
        }
    };

    /**
     * @return The largest rectangle with the aspect ratio of given width and height, centered within a given window.
     */
    public static Rectangle2D calcViewport(double inputViewWidth, double inputViewHeight, Rectangle2D window) {
        double windowWidth = window.getWidth();
        double windowHeight = window.getHeight();
        double windowAspectRatio = windowWidth / windowHeight;
        double inputAspectRatio = inputViewWidth / inputViewHeight;
        double xstart = 0, ystart = 0;
        double outputWidth = -1;
        double outputHeight = -1;
        if(inputAspectRatio < windowAspectRatio) {
            outputWidth = windowHeight * inputAspectRatio;
            outputHeight = windowHeight;
            double extra = windowWidth - outputWidth;
            xstart = extra / 2;
            ystart = 0;
        } else {
            outputWidth = windowWidth;
            outputHeight = windowWidth / inputAspectRatio;
            double extra = windowHeight - outputHeight;
            xstart = 0;
            ystart = extra / 2;
        }
        return new Rectangle2D.Double(xstart, ystart, outputWidth, outputHeight);
    }

    public void setImage(final BufferedImage img) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                picturePanel.setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
                //picturePanel.setBackground(Color.red); // For debugging viewport drawing.
                TwinkleUI.this.setIconImage(shared_image);
                boolean first_image = shared_image == null;
                shared_image = img;
                if(first_image)
                    pack();
                repaint();
            }
        });
    }

    public TwinkleUI() {
        super("Twinklebrot Explorer");
        JPanel sliders = new JPanel(new SpringLayout());
        addSlider(sliders, Twinklebrot.N_TRAJECTORIES_NAME, Twinklebrot.DEF_N_TRAJECTORIES, Twinklebrot.MIN_TRAJECTORIES, Twinklebrot.MAX_TRAJECTORIES, true);
        addSlider(sliders, Twinklebrot.MAX_ITERATIONS_NAME, Twinklebrot.MAX_ITERATIONS, Twinklebrot.MIN_MAX, Twinklebrot.MAX_MAX, true);
        addSlider(sliders, Twinklebrot.MIN_ITERATIONS_NAME, Twinklebrot.MIN_ITERATIONS, Twinklebrot.MIN_MIN, Twinklebrot.MAX_MIN, true);
        addSlider(sliders, Twinklebrot.NUM_SEGMENTS_NAME, Twinklebrot.DEF_SEGMENTS, Twinklebrot.MIN_MIN, Twinklebrot.MAX_MAX, true);
        addSlider(sliders, Twinklebrot.SCALE_NAME, Twinklebrot.DEF_SCALE, Twinklebrot.MIN_SCALE, Twinklebrot.MAX_SCALE, false);
        addSlider(sliders, Twinklebrot.AUDIO_TRACKS_NAME, Twinklebrot.DEF_AUDIO_TRACKS, Twinklebrot.MIN_AUDIO_TRACKS, Twinklebrot.MAX_AUDIO_TRACKS, true);
        addSlider(sliders, Twinklebrot.FPS_NAME, Twinklebrot.DEF_FPS, Twinklebrot.MIN_FPS, Twinklebrot.MAX_FPS, true);
        SpringUtilities.makeCompactGrid(sliders, sliders.getComponentCount() / 3, 3, 0, 0, 6, 1);
        final JPanel bulb_controls = new BulbControls();
        boolean initial_inmset = PropertyManager.getBoolean(Twinklebrot.IN_MSET_NAME, Twinklebrot.DEF_IN_MSET);
        bulb_controls.setVisible(initial_inmset);
        final JButton in_out = new JButton(initial_inmset ? NON_ESCAPERS : ESCAPERS);
        in_out.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                String cur = in_out.getText();
                boolean escapers = NON_ESCAPERS.equals(cur);
                bulb_controls.setVisible(!escapers);
                in_out.setText(escapers ? ESCAPERS : NON_ESCAPERS);
                PropertyManager.userprefs.setProperty(Twinklebrot.IN_MSET_NAME, "" + !NON_ESCAPERS.equals(cur));
            }
        });
        VCRControls vcr_controls = new VCRControls();
        PropertyManager.userprefs.setProperty(Twinklebrot.RENDERING_NAME, "" + true); // TODO: Respect user pref across sessions.
        vcr_controls.addVCRListener(new VCRControls.VCRListener() {
            @Override
            public void functionChanged(VCRButton.STATE newState) {
                switch(newState) {
                    case PLAYING:
                        PropertyManager.userprefs.setProperty(Twinklebrot.RENDERING_NAME, "" + true);
                        repaint();
                        break;
                    case PAUSED:
                        PropertyManager.userprefs.setProperty(Twinklebrot.RENDERING_NAME, "" + false);
                        break;
                    case STOPPED:
                        Twinklebrot.endWriting();
                        break;
                    case RECORDING:
                        Twinklebrot.beginWriting();
                        break;
                }
            }
        });
        final JCheckBox mirror = new JCheckBox("Mirror");
        mirror.setSelected(PropertyManager.getBoolean(Twinklebrot.MIRRORING_NAME, Twinklebrot.DEF_MIRRORING));
        mirror.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                PropertyManager.userprefs.setProperty(Twinklebrot.MIRRORING_NAME, "" + mirror.isSelected());
            }
        });
        JPanel buttons = new JPanel();
        buttons.setLayout(new BorderLayout());
        JPanel west_controls = new JPanel();
        west_controls.add(mirror);
        west_controls.add(in_out);
        west_controls.add(bulb_controls);
        buttons.add(west_controls, "West");
        buttons.add(vcr_controls, "East");
        buttons.add(Box.createVerticalStrut(50));
        // Create and populate the control panel.
        JPanel controls = new JPanel();
        controls.setBorder(new EmptyBorder(0, 6, 0, 6));
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(sliders);
        controls.add(buttons);
        JPanel root = new JPanel(new BorderLayout());
        root.add(picturePanel, "Center");
        root.add(controls, "South");
        getContentPane().add(root);
        setLocation(
            PropertyManager.getInt("XPOS", 0),
            PropertyManager.getInt("YPOS", 0));
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent evt) {
                int x = evt.getComponent().getX();
                int y = evt.getComponent().getY();
                PropertyManager.userprefs.setProperty("XPOS", "" + x);
                PropertyManager.userprefs.setProperty("YPOS", "" + y);
            }
        });
        picturePanel.setPreferredSize(new Dimension(640, 300));
        pack();
        setVisible(true);
    }
    private static void addSlider(JPanel into, final String name, float cur, float min, float max, final boolean round) {
        into.add(new JLabel(name));
        cur = PropertyManager.getFloat(name, cur);
        final FloatSlider f = new FloatSlider(Scrollbar.HORIZONTAL, cur, min, max, min > 0);
        into.add(f);
        final JLabel value = new JLabel("" + cur);
        value.setPreferredSize(new Dimension(44, 0));
        AdjustmentListener al = new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent ae) {
                double val = f.getFloatValue();
                String val_str = "" + val;
                String disp_val = round ? val_str.substring(0, val_str.indexOf('.')) : val_str.substring(0, 3);
                value.setText(disp_val);
                PropertyManager.userprefs.setProperty(name, "" + val);
            }
        };
        f.addAdjustmentListener(al);
        al.adjustmentValueChanged(null); // Goose slider to initialize label.
        into.add(value);
    }
}
