package sandipchitale.gistdoit;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

/**
 * Banner above gist files with changes not yet saved to GitHub.
 */
public class GistEditorNotificationProvider implements EditorNotificationProvider, DumbAware {
    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                  @NotNull VirtualFile file) {
        if (!(file instanceof LightVirtualFile lightVirtualFile) || !GistEditTracker.isModified(file)) {
            return null;
        }
        GistEditTracker gistEditTracker = GistEditTracker.getInstance(project);
        return fileEditor -> {
            EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
            panel.setText("Unsaved changes. They are saved to GitHub only with Save to Gist.");
            panel.createActionLabel("Save to Gist", () -> gistEditTracker.save(lightVirtualFile));
            panel.createActionLabel("Show Diff", () -> gistEditTracker.showDiff(file));
            panel.createActionLabel("Revert", () -> gistEditTracker.revert(file));
            return panel;
        };
    }
}
