package kookyohwan.gui;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.im.InputContext;

/** 버튼·입력 칸에서 대화상자를 띄우는 동작을 안전하게 실행하기 위한 도우미. */
final class UiEvents {
    private UiEvents() {
    }

    /**
     * 대화상자를 띄울 수 있는 동작은 입력 이벤트 처리 '안에서' 바로 실행하지 않고, 처리가 끝난 뒤에 실행한다.
     * macOS에서 한글 입력기가 글자를 조합하는 중(예: "나눔"의 마지막 글자)에 Enter나 버튼으로
     * 모달 대화상자를 바로 띄우면, 입력기는 이벤트 처리가 끝나기를 기다리고 대화상자는 입력기를 기다려
     * 창 전체가 멈춘다. 그래서 먼저 조합 중인 글자를 확정하고(endComposition) 다음 차례로 미룬다(invokeLater).
     */
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
