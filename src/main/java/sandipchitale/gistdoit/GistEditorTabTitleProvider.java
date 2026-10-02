package sandipchitale.gistdoit;

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marks tabs of gist files that have changes not yet saved to GitHub.
 */
public class GistEditorTabTitleProvider implements EditorTabTitleProvider, DumbAware {
    @Override
    public @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
        return GistEditTracker.isModified(file) ? "* " + file.getName() : null;
    }
}
