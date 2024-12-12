package sandipchitale.gistdoit;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractGistdoitAction extends AnAction {
    protected GistDoItToolWindow gistDoItToolWindow;

    void setGistDoItToolWindow(GistDoItToolWindow gistDoItToolWindow) {
        this.gistDoItToolWindow = gistDoItToolWindow;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
