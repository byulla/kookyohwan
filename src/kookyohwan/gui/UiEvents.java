package kookyohwan.gui;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.im.InputContext;

final class UiEvents {
    private UiEvents() {
    }

    static void afterInput(Runnable action) {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner != null) {
            InputContext context = owner.getInputContext();
            if (context != null) {
                context.endComposition();
            }
        }
        SwingUtilities.invokeLater(action);
    }
}
