package sandipchitale.gistdoit;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.apache.commons.io.IOUtils;
import org.kohsuke.github.GHGist;
import org.kohsuke.github.GHGistFile;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GistDoItToolWindow {
    private static final String GITHUB_TOKEN = "GITHUB_TOKEN";

    private final JPanel contentToolWindow;
    private final GistsTreeModel gistsTreeModel;

    private static class GistTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();
                if (node.getUserObject() instanceof String) {
                    String text = (String) node.getUserObject();
                    if (text.startsWith("#")) {
                        text = text.substring(1);
                    }
                    setText(text);
                } else if (userObject instanceof GHGist) {
                    GHGist gist = (GHGist) userObject;
                    Date updatedAt = null;
                    try {
                        updatedAt = gist.getUpdatedAt();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    setText(gist.getDescription() + (updatedAt == null ? "" : " [ Updated on: " + updatedAt + " ]"));
                } else if (userObject instanceof GHGistFile) {
                    GHGistFile gistFile = (GHGistFile) userObject;
                    String gistFileName = gistFile.getFileName();
                    setText(gistFile.getFileName());
                    FileType fileType = null;
                    if (gistFileName.contains(".")) {
                        String extension = gistFileName.substring(gistFileName.lastIndexOf(".") + 1);
                        fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension);
                    }
                    if (fileType != null) {
                        setIcon(fileType.getIcon());
                    } else {
                        setIcon(PlainTextFileType.INSTANCE.getIcon());
                    }
                }
            }
            return component;
        }
    }

    private static class GistsTreeModel extends DefaultTreeModel {
        private Set<GHGist> gists;

        public GistsTreeModel() {
            super(new DefaultMutableTreeNode("Gists", true));
        }

        public void setGists(Set<GHGist> gists) {
            this.gists = gists;
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
            root.removeAllChildren();
            Map<String, Set<GHGist>> gistsMap = new TreeMap<>();
            for (GHGist gist : gists) {
                String gistDescription = gist.getDescription();
                if (gistDescription.contains(" #")) {
                    // Categorized
                    String[] gistDescriptionParts = gistDescription.split("\\s+");
//                    gistDescription = gistDescription.substring(0, gistDescription.indexOf(" #")).trim();
                    for (int i = gistDescriptionParts.length - 1; i >= 0; i--) {
                        String gistDescriptionPart = gistDescriptionParts[i];
                        if (gistDescriptionPart.startsWith("#") && gistDescriptionPart.length() > 1) {
                            gistDescriptionPart = gistDescriptionPart.substring(1); // remove # from category name
                            Set<GHGist> gistSet = gistsMap.computeIfAbsent(gistDescriptionPart, k -> new LinkedHashSet<>());
                            gistSet.add(gist);
                            gistsMap.put(gistDescriptionPart, gistSet);
                        } else {
                            break;
                        }
                    }
                } else {
                    // Uncategorized
                    Set<GHGist> gistSet = gistsMap.computeIfAbsent("", k -> new LinkedHashSet<>());
                    gistSet.add(gist);
                    gistsMap.put("", gistSet);
                }

            }
            for (String category: gistsMap.keySet()) {
                Set<GHGist> gistSet = gistsMap.get(category);
                if (gistSet.size() == 0) {
                    continue;
                }
                DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(category);
                for (GHGist gist : gistSet) {
                    String description = gist.getDescription();
                    if (description.contains(" #")) {
                        description = description.substring(0, description.indexOf(" #")).trim();
                    }
                    DefaultMutableTreeNode gistNode = new DefaultMutableTreeNode(gist);
                    categoryNode.add(gistNode);
                    gist.getFiles().forEach((name, file) -> {
                        DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(file);
                        gistNode.add(fileNode);
                    });
                }
                root.add(categoryNode);
            }
        }

        public Set<GHGist> getGists() {
            return gists;
        }
    }

    public GistDoItToolWindow(Project project) {
        this.contentToolWindow = new SimpleToolWindowPanel(true, true);

        gistsTreeModel = new GistsTreeModel();

        Tree tree = new Tree(gistsTreeModel);

        tree.setCellRenderer(new GistTreeCellRenderer());

        tree.setRootVisible(true);

        tree.getSelectionModel().setSelectionMode(DefaultTreeSelectionModel.SINGLE_TREE_SELECTION);

        tree.addTreeSelectionListener(tse -> {
            Object lastPathComponent = tse.getPath().getLastPathComponent();
            if (lastPathComponent instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode) lastPathComponent;
                Object userObject = defaultMutableTreeNode.getUserObject();
                if (userObject instanceof GHGistFile) {
                    GHGistFile gistFile = ((GHGistFile) userObject);
                    String gistFileName = gistFile.getFileName();
                    String gistFileRawUrl = gistFile.getRawUrl();
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        tree.setPaintBusy(true);
                        try {
                            String content = IOUtils.toString(new URL(gistFileRawUrl), StandardCharsets.UTF_8);
                            ApplicationManager.getApplication().invokeLater(() -> {
                                FileType fileType = null;
                                if (gistFileName.contains(".")) {
                                    String extension = gistFileName.substring(gistFileName.lastIndexOf(".") + 1);
                                    fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension);
                                }
                                if (fileType == null) {
                                    fileType = PlainTextFileType.INSTANCE;
                                }
                                LightVirtualFile lightVirtualFile = new LightVirtualFile(gistFileName, fileType, content);
//                                lightVirtualFile.setWritable(false);
                                // Figure out a way to set language for syntax highlighting based on file extension
                                lightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
                                FileEditorManager.getInstance(project).openFile(lightVirtualFile, true);
                            });
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            tree.setPaintBusy(false);
                        }
                    });
                }

            }
        });
        this.contentToolWindow.add(new JBScrollPane(tree), BorderLayout.CENTER);
        tree.setPaintBusy(true);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ApplicationManager.getApplication().runReadAction(() -> {
                try {
                    String githubToken = System.getProperty(GITHUB_TOKEN, System.getenv(GITHUB_TOKEN));
                    if (githubToken == null) {
                        Notification notification = new Notification("gistDoItNotificationGroup",
                                "Github token not set",
                                String.format("Set Github access token as system property %s or environment variable %s", GITHUB_TOKEN, GITHUB_TOKEN),
                                NotificationType.ERROR);
                        notification.notify(project);
                        return;
                    }
                    GitHub github = new GitHubBuilder().withOAuthToken(githubToken).build();
                    Set<GHGist> gists = github.getMyself().listGists().toSet();
                    SwingUtilities.invokeLater(() -> {
                        gistsTreeModel.setGists(gists);
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    tree.setPaintBusy(false);
                }
            });
        });
    }

    public JComponent getContent() {
        return this.contentToolWindow;
    }
}