package kookyohwan.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

public final class Theme {
    static final Color BACKGROUND = new Color(0xF4F5F7);
    static final Color CARD = Color.WHITE;
    static final Color LINE = new Color(0xE5E7EB);
    static final Color TEXT = new Color(0x1F2328);
    static final Color MUTED = new Color(0x6B7280);
    static final Color ACCENT = new Color(0xE9453A);
    static final Color ACCENT_DARK = new Color(0xCB362C);
    static final Color ACCENT_SOFT = new Color(0xFDECEA);
    static final Color GREEN = new Color(0x18794E);
    static final Color GREEN_SOFT = new Color(0xE3F6EA);
    static final Color BLUE = new Color(0x1D5FD1);
    static final Color BLUE_SOFT = new Color(0xE8F0FE);
    static final Color AMBER = new Color(0x9A6700);
    static final Color AMBER_SOFT = new Color(0xFFF4D6);
    static final Color GRAY_SOFT = new Color(0xEEF0F3);
    static final Color ROW_MINE = new Color(0xF5F9FF);
    static final Color HEADER = new Color(0xF8F9FB);
    static final Color INPUT_BORDER = new Color(0xD1D5DB);
    static final int INPUT_HEIGHT = 40;
    static final int INPUT_ARC = 20;

    private Theme() {
    }

    public static void install() {
        String family = koreanFamily();
        for (String key : new String[]{"Label.font", "Button.font", "Table.font", "TableHeader.font", "TextField.font",
                "ComboBox.font", "TextArea.font", "OptionPane.messageFont", "OptionPane.buttonFont"}) {
            Font current = UIManager.getFont(key);
            if (current != null) {
                UIManager.put(key, new FontUIResource(family == null ? current.deriveFont(13f)
                        : new Font(family, current.getStyle(), 13)));
            }
        }
    }

    private static String koreanFamily() {
        List<String> installed = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String name : new String[]{"Apple SD Gothic Neo", "Malgun Gothic"}) {
            if (installed.contains(name)) {
                return name;
            }
        }
        return null;
    }

    static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
        label.setForeground(TEXT);
        return label;
    }

    static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        return label;
    }

    static JButton primary(String text) {
        return new RoundButton(text, true);
    }

    static JButton secondary(String text) {
        return new RoundButton(text, false);
    }

    static JPanel card(Component content) {
        RoundPanel card = new RoundPanel(14, CARD, LINE);
        card.setLayout(new java.awt.BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        card.add(content);
        return card;
    }

    static void transparent(Component component) {
        if (component instanceof JPanel && !(component instanceof RoundPanel)) {
            ((JPanel) component).setOpaque(false);
        }
        if (component instanceof JScrollPane || component instanceof JTable) {
            return;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                transparent(child);
            }
        }
    }

    static JScrollPane scroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        scroll.getViewport().setBackground(CARD);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        if (view instanceof JTable) {
            JPanel corner = new JPanel();
            corner.setBackground(HEADER);
            corner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
            scroll.setCorner(JScrollPane.UPPER_RIGHT_CORNER, corner);
        }
        return scroll;
    }

    static void table(JTable table, IntPredicate rowTint) {
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(LINE);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(ACCENT_SOFT);
        table.setSelectionForeground(TEXT);
        table.setForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new CellRenderer(rowTint));

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 36));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, false, false, row, column);
                label.setBackground(HEADER);
                label.setForeground(MUTED);
                label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
                label.setHorizontalAlignment(SwingConstants.LEFT);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), BorderFactory.createEmptyBorder(0, 10, 0, 6)));
                return label;
            }
        });
    }

    static TableCellRenderer pill(IntPredicate rowTint) {
        return new PillRenderer(rowTint);
    }

    private static Color[] pillColors(String text) {
        if (text.startsWith("마감") || text.startsWith("제외")) {
            return new Color[]{ACCENT_SOFT, ACCENT_DARK};
        }
        if (text.startsWith("빈자리") || text.startsWith("가능") || text.startsWith("매칭완료") || text.startsWith("내 과목")) {
            return new Color[]{GREEN_SOFT, GREEN};
        }
        if (text.startsWith("겹침")) {
            return new Color[]{AMBER_SOFT, AMBER};
        }
        if (text.startsWith("대기") || text.startsWith("내 요청")) {
            return new Color[]{BLUE_SOFT, BLUE};
        }
        return new Color[]{GRAY_SOFT, MUTED};
    }

    private static Color rowBackground(JTable table, boolean selected, int row, IntPredicate rowTint) {
        if (selected) {
            return table.getSelectionBackground();
        }
        return rowTint != null && rowTint.test(row) ? ROW_MINE : CARD;
    }

    private static final class CellRenderer extends DefaultTableCellRenderer {
        private final IntPredicate rowTint;

        private CellRenderer(IntPredicate rowTint) {
            this.rowTint = rowTint;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, false, row, column);
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 6));
            label.setBackground(rowBackground(table, selected, row, rowTint));
            label.setForeground(TEXT);
            return label;
        }
    }

    private static final class PillRenderer extends JComponent implements TableCellRenderer {
        private final IntPredicate rowTint;
        private String text = "";
        private Color background = CARD;

        private PillRenderer(IntPredicate rowTint) {
            this.rowTint = rowTint;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            text = value == null ? "" : value.toString();
            background = rowBackground(table, selected, row, rowTint);
            setFont(table.getFont().deriveFont(Font.BOLD, 12f));
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(background);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (!text.isEmpty()) {
                Color[] colors = pillColors(text);
                FontMetrics metrics = g.getFontMetrics(getFont());
                int width = Math.min(getWidth() - 12, metrics.stringWidth(text) + 20);
                int height = 22;
                int x = 8;
                int y = (getHeight() - height) / 2;
                g.setColor(colors[0]);
                g.fillRoundRect(x, y, width, height, height, height);
                g.setColor(colors[1]);
                g.setFont(getFont());

                g.clipRect(x, y, width - 8, height);
                g.drawString(text, x + 10, y + (height + metrics.getAscent() - metrics.getDescent()) / 2);
            }
            g.dispose();
        }
    }

    static JTextField field(int columns) {
        return new RoundField(columns);
    }

    static <T> JComboBox<T> combo() {
        return new RoundCombo<>();
    }

    static <T> JComboBox<T> combo(T[] items) {
        JComboBox<T> box = new RoundCombo<>();
        for (T item : items) {
            box.addItem(item);
        }
        return box;
    }

    private static void paintInput(Graphics graphics, JComponent c, boolean focused) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c.isEnabled() ? CARD : GRAY_SOFT);
        g.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, INPUT_ARC, INPUT_ARC);
        g.setColor(focused ? ACCENT : INPUT_BORDER);
        g.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, INPUT_ARC, INPUT_ARC);
        g.dispose();
    }

    private static final class RoundField extends JTextField {
        private RoundField(int columns) {
            super(columns);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 14, 2, 14));
            setFont(getFont().deriveFont(14f));
            setForeground(TEXT);
            setCaretColor(TEXT);
            setSelectionColor(ACCENT_SOFT);
            setSelectedTextColor(TEXT);
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    repaint();
                }
            });
        }

        @Override
        public void updateUI() {
            setUI(new BasicTextFieldUI());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, INPUT_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintInput(g, this, isFocusOwner());
            super.paintComponent(g);
        }
    }

    private static final class RoundCombo<T> extends JComboBox<T> {
        private RoundCombo() {
            setOpaque(false);
            setBackground(CARD);
            setForeground(TEXT);
            setFont(getFont().deriveFont(14f));
            setBorder(BorderFactory.createEmptyBorder(2, 14, 2, 6));
            setMaximumRowCount(12);
            setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean selected, boolean focus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                    label.setBorder(index < 0 ? null : BorderFactory.createEmptyBorder(7, 12, 7, 12));
                    label.setBackground(selected && index >= 0 ? ACCENT_SOFT : CARD);
                    label.setForeground(TEXT);
                    return label;
                }
            });
        }

        @Override
        public void updateUI() {
            setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton arrow = new JButton() {
                        @Override
                        protected void paintComponent(Graphics graphics) {
                            Graphics2D g = (Graphics2D) graphics.create();
                            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g.setColor(MUTED);
                            g.setStroke(new java.awt.BasicStroke(1.8f, java.awt.BasicStroke.CAP_ROUND,
                                    java.awt.BasicStroke.JOIN_ROUND));
                            int cx = getWidth() / 2;
                            int cy = getHeight() / 2;
                            g.drawPolyline(new int[]{cx - 5, cx, cx + 5}, new int[]{cy - 2, cy + 3, cy - 2}, 3);
                            g.dispose();
                        }
                    };
                    arrow.setUI(new BasicButtonUI());
                    arrow.setContentAreaFilled(false);
                    arrow.setBorderPainted(false);
                    arrow.setFocusable(false);
                    arrow.setOpaque(false);
                    arrow.setPreferredSize(new Dimension(28, INPUT_HEIGHT));
                    return arrow;
                }

                @Override
                public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle bounds, boolean focus) {
                }

                @Override
                protected ComboPopup createPopup() {
                    BasicComboPopup popup = new BasicComboPopup(comboBox);
                    popup.setBorder(BorderFactory.createLineBorder(LINE));
                    return popup;
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width + 26, INPUT_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintInput(g, this, isPopupVisible());
            super.paintComponent(g);
        }
    }

    static final class RoundPanel extends JPanel {
        private final int radius;
        private final Color fill;
        private final Color border;

        RoundPanel(int radius, Color fill, Color border) {
            this.radius = radius;
            this.fill = fill;
            this.border = border;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (border != null) {
                g.setColor(border);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g.dispose();
        }
    }

    private static final class RoundButton extends JButton {
        private final boolean primary;

        private RoundButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setUI(new BasicButtonUI());
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(getFont().deriveFont(Font.BOLD, 13f));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            return new Dimension(metrics.stringWidth(getText()) + 40, INPUT_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean hover = getModel().isRollover() || getModel().isPressed();
            Color fill;
            Color text;
            Color border = null;
            if (!isEnabled()) {
                fill = GRAY_SOFT;
                text = new Color(0xA3A9B3);
            } else if (primary) {
                fill = hover ? ACCENT_DARK : ACCENT;
                text = Color.WHITE;
            } else {
                fill = hover ? HEADER : CARD;
                text = TEXT;
                border = INPUT_BORDER;
            }
            int arc = INPUT_ARC;
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            if (border != null) {
                g.setColor(border);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            FontMetrics metrics = g.getFontMetrics(getFont());
            g.setFont(getFont());
            g.setColor(text);
            g.drawString(getText(), (getWidth() - metrics.stringWidth(getText())) / 2,
                    (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
            g.dispose();
        }
    }

    static final class NavButton extends JButton {
        private boolean selectedTab;

        NavButton(String text) {
            super(text);
            setUI(new BasicButtonUI());
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(getFont().deriveFont(Font.BOLD, 15f));
        }

        void setSelectedTab(boolean selectedTab) {
            this.selectedTab = selectedTab;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(getFontMetrics(getFont()).stringWidth(getText()) + 30, 56);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics metrics = g.getFontMetrics(getFont());
            g.setFont(getFont());
            g.setColor(selectedTab ? ACCENT : getModel().isRollover() ? TEXT : MUTED);
            int textWidth = metrics.stringWidth(getText());
            g.drawString(getText(), (getWidth() - textWidth) / 2,
                    (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
            if (selectedTab) {
                g.fillRect((getWidth() - textWidth) / 2 - 2, getHeight() - 4, textWidth + 4, 4);
            }
            g.dispose();
        }
    }
}
