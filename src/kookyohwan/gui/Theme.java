package kookyohwan.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
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
import java.awt.RenderingHints;
import java.util.function.IntPredicate;

/**
 * 화면 모양을 한 곳에서 정한다: 색, 글꼴, 둥근 버튼, 흰 카드, 표 모양, 상태 알약(pill).
 * 입력 칸과 선택 상자는 운영체제 모양을 그대로 쓰고, 버튼·탭·표처럼 눈에 많이 띄는 것만 직접 그린다.
 */
public final class Theme {
    static final Color BACKGROUND = new Color(0xF4F5F7);
    static final Color CARD = Color.WHITE;
    static final Color LINE = new Color(0xE5E7EB);
    static final Color TEXT = new Color(0x1F2328);
    static final Color MUTED = new Color(0x6B7280);
    static final Color ACCENT = new Color(0xE9453A);      // 시간표 앱의 빨강
    static final Color ACCENT_DARK = new Color(0xCB362C);
    static final Color ACCENT_SOFT = new Color(0xFDECEA);
    static final Color GREEN = new Color(0x18794E);
    static final Color GREEN_SOFT = new Color(0xE3F6EA);
    static final Color BLUE = new Color(0x1D5FD1);
    static final Color BLUE_SOFT = new Color(0xE8F0FE);
    static final Color GRAY_SOFT = new Color(0xEEF0F3);
    static final Color ROW_MINE = new Color(0xF5F9FF);
    static final Color HEADER = new Color(0xF8F9FB);

    private Theme() {
    }

    /** 기본 글꼴 크기를 조금 키운다. 화면을 만들기 전에 한 번 부른다. */
    public static void install() {
        for (String key : new String[]{"Label.font", "Button.font", "Table.font", "TableHeader.font", "TextField.font",
                "ComboBox.font", "TextArea.font", "OptionPane.messageFont", "OptionPane.buttonFont"}) {
            Font current = UIManager.getFont(key);
            if (current != null) {
                UIManager.put(key, new FontUIResource(current.deriveFont(13f)));
            }
        }
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

    /** 강조 버튼: 빨강 바탕에 흰 글씨. 화면마다 가장 중요한 동작 하나에만 쓴다. */
    static JButton primary(String text) {
        return new RoundButton(text, true);
    }

    /** 보조 버튼: 흰 바탕에 테두리. */
    static JButton secondary(String text) {
        return new RoundButton(text, false);
    }

    /** 흰 바탕의 둥근 카드에 내용을 담는다. */
    static JPanel card(Component content) {
        RoundPanel card = new RoundPanel(14, CARD, LINE);
        card.setLayout(new java.awt.BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        card.add(content);
        return card;
    }

    /** 카드 안에 들어갈 패널들은 바탕을 칠하지 않게 한다 (카드의 흰색이 보이도록). */
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
        if (view instanceof JTable) { // 스크롤바 위의 빈 모서리도 머리글과 같은 색으로
            JPanel corner = new JPanel();
            corner.setBackground(HEADER);
            corner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
            scroll.setCorner(JScrollPane.UPPER_RIGHT_CORNER, corner);
        }
        return scroll;
    }

    /** 표: 줄 간격을 넓히고 세로선을 없애고 머리글을 옅은 회색으로. rowTint가 참인 줄은 옅은 파랑(내 요청). */
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

    /** 상태를 알약 모양으로 그리는 칸 (가능/제외, 대기/매칭완료/취소, 마감/빈자리, 내 요청). */
    static TableCellRenderer pill(IntPredicate rowTint) {
        return new PillRenderer(rowTint);
    }

    /** 글자에 따라 알약의 {바탕, 글자} 색. */
    private static Color[] pillColors(String text) {
        if (text.startsWith("마감") || text.startsWith("제외")) {
            return new Color[]{ACCENT_SOFT, ACCENT_DARK};
        }
        if (text.startsWith("빈자리") || text.startsWith("가능") || text.startsWith("매칭완료")) {
            return new Color[]{GREEN_SOFT, GREEN};
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

    /** 일반 칸: 왼쪽 여백을 두고, 줄 바탕색(선택/내 요청)을 맞춘다. */
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
                // setClip으로 바꾸면 표가 정해 준 그리기 영역을 무시해서, 반쯤 가려진 줄의 알약이 표 밖으로 삐져나온다.
                // clipRect는 기존 영역과 겹치는 부분만 남긴다
                g.clipRect(x, y, width - 8, height);
                g.drawString(text, x + 10, y + (height + metrics.getAscent() - metrics.getDescent()) / 2);
            }
            g.dispose();
        }
    }

    /** 모서리가 둥근 패널 (카드, 사용자 표시). */
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

    /** 직접 그리는 둥근 버튼. 운영체제 버튼은 색을 바꿀 수 없어서 따로 그린다. */
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
            return new Dimension(metrics.stringWidth(getText()) + 34, 34);
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
                border = new Color(0xD1D5DB);
            }
            int arc = 10;
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

    /** 위쪽 메뉴의 탭 글자. 고른 탭은 빨간 글씨에 아래 빨간 줄 (시간표 앱의 메뉴 모양). */
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
