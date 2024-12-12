package sandipchitale.gistdoit;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class ConnectToGithubAction extends AbstractGistdoitAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (gistDoItToolWindow != null) {
            gistDoItToolWindow.connectToGithub();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean enabled = true;
        if (gistDoItToolWindow != null) {
            enabled = !gistDoItToolWindow.isConnected();
        }
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
