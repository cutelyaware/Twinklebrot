import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;


@SuppressWarnings("serial")
public class BulbControls extends JPanel {
    public final static String PREFIX = "Bulb ";
    private final static String[] TOOL_TIPS = {
        "Smaller bulbs",
        "Main Cartoid",
        "Head",
        "Hands",
        "Top Knot",
    };
    public BulbControls() {
        super();
        JPanel checkboxes = new JPanel();
        for(int i = 0; i <= 4; i++) {
            final int final_i = i;
            final String bulb_name = PREFIX + final_i;
            final JCheckBox bulb = new JCheckBox("" + final_i);
            bulb.setSelected(PropertyManager.getBoolean(bulb_name, true));
            bulb.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    PropertyManager.userprefs.setProperty(bulb_name, "" + bulb.isSelected());
                }
            });
            bulb.setToolTipText(TOOL_TIPS[i]);
            checkboxes.add(bulb);
        }
        // Rename and move special case "other" bulb check box to the end.
        JCheckBox bulb0 = (JCheckBox) checkboxes.getComponent(0);
        checkboxes.remove(bulb0);
        bulb0.setText("Other");
        checkboxes.add(bulb0);
        add(new JLabel("Bulbs:"));
        add(checkboxes);
    }
}
