package sandipchitale.gistdoit;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHGist;
import org.kohsuke.github.GHGistFile;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static javax.swing.JOptionPane.NO_OPTION;
import static javax.swing.JOptionPane.QUESTION_MESSAGE;

public class GistDoItToolWindow extends SimpleToolWindowPanel {
    private static final String GITHUB_TOKEN = "GITHUB_TOKEN";

    private static GitHub github;

    private final JPanel contentToolWindow;

    private final GistsTreeModel gistsTreeModel;
    private final JLabel loadingGistsLabel;
    private final JButton connectToGithubButton;
    private final JButton disconnectFromGithubButton;
    private final JButton refreshGistsButton;
    private final Project project;

    private static class GistTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree,
                                          Object value,
                                          boolean sel,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
            if (expanded) {
                setIcon(AllIcons.Nodes.Folder);
            } else {
                setIcon(AllIcons.Nodes.Folder);
            }
            if (value instanceof DefaultMutableTreeNode node) {
                Object userObject = node.getUserObject();
                if (node.getUserObject() instanceof String text) {
                    if (text.startsWith("#")) {
                        text = text.substring(1);
                    }
                    append(text);
                    setIcon(AllIcons.Actions.GroupBy);
                } else if (userObject instanceof GHGist gist) {
                    Date updatedAt = null;
                    try {
                        updatedAt = gist.getUpdatedAt();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    String description = gist.getDescription();
                    if (description.contains("#")) {
                        description = description.substring(0, description.indexOf("#")).trim();
                    }
                    append(description + (updatedAt == null ? "" : " [ Updated on: " + updatedAt + " ]"));
                    setIcon(AllIcons.Actions.ListFiles);
                } else if (userObject instanceof GHGistFile gistFile) {
                    String gistFileName = gistFile.getFileName();
                    append(gistFile.getFileName());
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
        }
    }

    private static class GistsTreeModel extends DefaultTreeModel {
        private Set<GHGist> gists;

        public GistsTreeModel() {
            super(new DefaultMutableTreeNode("Gists", true));
        }

        public void setGists(Set<GHGist> gists) {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
            try {
                root.removeAllChildren();
                this.gists = gists;
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
                    if (gistSet.isEmpty()) {
                        continue;
                    }
                    DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(category);
                    for (GHGist gist : gistSet) {
                        DefaultMutableTreeNode gistNode = new DefaultMutableTreeNode(gist);
                        categoryNode.add(gistNode);
                        gist.getFiles().forEach((name, file) -> {
                            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(file);
                            gistNode.add(fileNode);
                        });
                    }
                    root.add(categoryNode);
                }
            } finally   {
                reload(root);
            }
        }

        public Set<GHGist> getGists() {
            return gists;
        }
    }

    public GistDoItToolWindow(Project project) {
        super(true, true);
        this.project = project;
        this.contentToolWindow = new BorderLayoutPanel();
        setContent(contentToolWindow);

        gistsTreeModel = new GistsTreeModel();

        Tree tree = new Tree(gistsTreeModel);

        GistTreeCellRenderer gistTreeCellRenderer = new GistTreeCellRenderer();
        tree.setCellRenderer(new GistTreeCellRenderer());

        tree.setRootVisible(true);

        tree.getSelectionModel().setSelectionMode(DefaultTreeSelectionModel.SINGLE_TREE_SELECTION);

        tree.addTreeSelectionListener(tse -> {
            Object lastPathComponent = tse.getPath().getLastPathComponent();
            if (lastPathComponent instanceof DefaultMutableTreeNode defaultMutableTreeNode) {
                Object userObject = defaultMutableTreeNode.getUserObject();
                if (userObject instanceof GHGistFile gistFile) {
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
                                lightVirtualFile.setWritable(false);
                                // Figure out a way to set language for syntax highlighting based on file extension
                                // Set language for syntax highlighting based on file type
                                Language language = LanguageUtil.getFileLanguage(lightVirtualFile);
                                if (language != null) {
                                    lightVirtualFile.setLanguage(language);
                                } else {
                                    lightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
                                }
                                FileEditor[] fileEditors = FileEditorManager.getInstance(project).openFile(lightVirtualFile, true);
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

        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        toolBar.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 2));

        loadingGistsLabel = new JLabel("Loading gists...");
        toolBar.add(loadingGistsLabel);
        loadingGistsLabel.setVisible(false);

        connectToGithubButton = new JButton(AllIcons.Vcs.Vendors.Github);
        connectToGithubButton.setToolTipText("Connect to Github...");
        connectToGithubButton.addActionListener(e -> {
            try {
                JPanel panel = new JPanel();
                JLabel label = new JLabel("Enter a GITHUB_TOKEN:");
                JPasswordField githubTokenPasswordField = new JPasswordField(42);
                panel.add(label);
                panel.add(githubTokenPasswordField);
                String[] options = new String[]{"OK", "Cancel"};
                int option = JOptionPane.showOptionDialog(
                    contentToolWindow.getTopLevelAncestor(),
                    panel,
                    "Enter GITHUB_TOKEN",
                    NO_OPTION,
                    QUESTION_MESSAGE,
                    null, options, options[1]);
                if (option == 0) {
                    GitHub gitHub = ensureGithubConnection(new String(githubTokenPasswordField.getPassword()));
                    if (gitHub != null) {
                        loadGists(github, project, tree);
                    }
                }
            } finally {
                adjustStates();
            }
        });
        toolBar.add(connectToGithubButton);

        refreshGistsButton = new JButton(AllIcons.Actions.Refresh);
        refreshGistsButton.setToolTipText("Reload Gists");
        refreshGistsButton.addActionListener(e -> {
            loadGists(github, project, tree);
        });
        toolBar.add(refreshGistsButton);

        disconnectFromGithubButton = new JButton(AllIcons.Actions.Close);
        disconnectFromGithubButton.setToolTipText("Disconnect from Github");
        disconnectFromGithubButton.addActionListener(e -> {
            github = null;
            gistsTreeModel.setGists(Collections.emptySet());
            adjustStates();
        });
        toolBar.add(disconnectFromGithubButton);

        this.contentToolWindow.add(toolBar, BorderLayout.NORTH);

        adjustStates();
        String githubToken = System.getProperty(GITHUB_TOKEN, System.getenv(GITHUB_TOKEN));
        if (githubToken == null) {
            Notification notification = new Notification("gistDoItNotificationGroup",
                "Github token not set",
                String.format("Set Github access token as system property %s or environment variable %s", GITHUB_TOKEN, GITHUB_TOKEN),
                NotificationType.ERROR);
            notification.notify(project);
            return;
        }

        // Try loading Gists - assume specified GitHub token is good
        loadGists(ensureGithubConnection(githubToken), project, tree);
    }

    private void adjustStates() {
        connectToGithubButton.setVisible(github == null);
        refreshGistsButton.setVisible(github != null);
        disconnectFromGithubButton.setVisible(github != null);
    }

    private GitHub ensureGithubConnection(String githubToken) {
        try {
            if (github == null) {
                try {
                    github = new GitHubBuilder().withOAuthToken(githubToken).build();

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return github;
        } finally {
            adjustStates();
        }
    }

    private void loadGists(GitHub github, Project project, Tree tree) {
        if (github == null) {
            Notification notification = new Notification("gistDoItNotificationGroup",
                "Not connected to Github",
                "Please connect to github first.",
                NotificationType.ERROR);
            notification.notify(project);
            return;
        }
        tree.setPaintBusy(true);
        loadingGistsLabel.setVisible(true);
        contentToolWindow.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ApplicationManager.getApplication().runReadAction(() -> {
                try {
                    try {
                        // Clear
                        SwingUtilities.invokeAndWait(() -> {
                            ((GistsTreeModel) tree.getModel()).setGists(Collections.emptySet());
                        });
                    } catch (InvocationTargetException | InterruptedException ignore) {
                    }
                    Set<GHGist> gists = github.getMyself().listGists().toSet();
                    SwingUtilities.invokeLater(() -> {
                        ((GistsTreeModel) tree.getModel()).setGists(gists);
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    contentToolWindow.setCursor(null);
                    tree.setPaintBusy(false);
                    loadingGistsLabel.setVisible(false);
                }
            });
        });
    }
}