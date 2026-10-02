package sandipchitale.gistdoit;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

/**
 * Tracks unsaved edits of gist files, keeping the tab marker and editor banner up to date, and
 * offers to save or reopen gist files closed with unsaved edits.
 */
@Service(Service.Level.PROJECT)
public final class GistEditTracker implements Disposable {
    private static final Key<Boolean> MODIFIED_KEY = Key.create("sandipchitale.gistdoit.modified");

    private final Project project;
    private GistDoItToolWindow gistDoItToolWindow;

    public GistEditTracker(@NotNull Project project) {
        this.project = project;
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
                if (file != null && file.getUserData(GistDoItToolWindow.GIST_KEY) != null) {
                    // Only refresh when the modified state flips, not on every keystroke
                    boolean modified = isModified(file);
                    if (!Boolean.valueOf(modified).equals(file.getUserData(MODIFIED_KEY))) {
                        file.putUserData(MODIFIED_KEY, modified);
                        refresh(file);
                    }
                }
            }
        }, this);
        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if (!project.isDisposed() && file instanceof LightVirtualFile lightVirtualFile
                        && isModified(file) && !source.isFileOpen(file)) {
                    notifyClosedWithUnsavedEdits(lightVirtualFile);
                }
            }
        });
    }

    static GistEditTracker getInstance(@NotNull Project project) {
        return project.getService(GistEditTracker.class);
    }

    void setGistDoItToolWindow(GistDoItToolWindow gistDoItToolWindow) {
        this.gistDoItToolWindow = gistDoItToolWindow;
    }

    /**
     * @return true if the gist file's text differs from the content the edit is based on
     */
    static boolean isModified(@NotNull VirtualFile file) {
        String baseContent = file.getUserData(GistDoItToolWindow.BASE_CONTENT_KEY);
        if (file.getUserData(GistDoItToolWindow.GIST_KEY) == null || baseContent == null) {
            return false;
        }
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        return document != null && !baseContent.equals(document.getText());
    }

    void refresh(@NotNull VirtualFile file) {
        file.putUserData(MODIFIED_KEY, isModified(file));
        FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file);
        EditorNotifications.getInstance(project).updateNotifications(file);
    }

    void save(@NotNull LightVirtualFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (gistDoItToolWindow != null && document != null) {
            gistDoItToolWindow.saveToGist(file, document.getText());
        }
    }

    void showDiff(@NotNull VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        String baseContent = file.getUserData(GistDoItToolWindow.BASE_CONTENT_KEY);
        if (document == null || baseContent == null) {
            return;
        }
        DiffContentFactory diffContentFactory = DiffContentFactory.getInstance();
        DiffManager.getInstance().showDiff(project, new SimpleDiffRequest(file.getName() + ": Unsaved Changes",
                diffContentFactory.create(project, baseContent, file.getFileType()),
                diffContentFactory.create(project, document.getText(), file.getFileType()),
                "Gist",
                "Yours"));
    }

    void revert(@NotNull VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        String baseContent = file.getUserData(GistDoItToolWindow.BASE_CONTENT_KEY);
        if (document != null && baseContent != null) {
            WriteCommandAction.runWriteCommandAction(project, "Revert Gist File", null,
                    () -> document.setText(baseContent));
        }
    }

    private void notifyClosedWithUnsavedEdits(@NotNull LightVirtualFile file) {
        // The edits stay in the file's document, so reopening restores them
        new Notification(GistDoItToolWindow.NOTIFICATION_GROUP,
                "Unsaved gist edits",
                file.getName() + " was closed with changes that are not saved to GitHub.",
                NotificationType.WARNING)
                .addAction(NotificationAction.createSimpleExpiring("Save to Gist", () -> save(file)))
                .addAction(NotificationAction.createSimpleExpiring("Reopen",
                        () -> FileEditorManager.getInstance(project).openFile(file, true)))
                .addAction(NotificationAction.createSimpleExpiring("Discard", () -> revert(file)))
                .notify(project);
    }

    @Override
    public void dispose() {
    }
}
