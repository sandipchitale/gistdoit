package sandipchitale.gistdoit;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

public class SaveToGistAction extends AbstractGistdoitAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (gistDoItToolWindow == null || !(file instanceof LightVirtualFile lightVirtualFile)) {
            return;
        }
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
            gistDoItToolWindow.saveToGist(lightVirtualFile, document.getText());
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        // Only shown for files opened from the user's own gists
        e.getPresentation().setEnabledAndVisible(gistDoItToolWindow != null
                && file != null
                && file.getUserData(GistDoItToolWindow.GIST_KEY) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
