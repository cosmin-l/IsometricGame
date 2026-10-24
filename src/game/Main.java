package game;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Depths of Tristfall");
            GamePanel panel = new GamePanel();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    panel.autoSave();
                }
            });
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.start();
        });
    }
}
