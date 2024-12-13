package sandipchitale.gistdoit;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.*;

import javax.swing.Timer;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static javax.swing.JOptionPane.DEFAULT_OPTION;
import static javax.swing.JOptionPane.QUESTION_MESSAGE;

public class GistDoItToolWindow extends SimpleToolWindowPanel {
    private static final String GITHUB_TOKEN = "GITHUB_TOKEN";

    private static GitHub github;
    private static GHUser githubUser;

    private final GistsTreeModel gistsTreeModel;
    private final Project project;
    private final Tree gistsTree;

    private class GistTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree,
                                          Object value,
                                          boolean sel,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
            setIcon(AllIcons.Nodes.Folder);
            if (value instanceof DefaultMutableTreeNode node) {
                Object userObject = node.getUserObject();
                if (node.getUserObject() instanceof String text) {
                    if (text.startsWith("#")) {
                        text = text.substring(1);
                    }
                    append(text);
                    if (node.isRoot()) {
                        if (isConnected()) {
                            try {
                                // Show gist owner info
                                append(String.format("Gists by %s ( %s )", githubUser.getName(), githubUser.getLogin()));
                            } catch (IOException ignore) {
                            }
                        }
                        setIcon(AllIcons.Vcs.Vendors.Github);
                    } else {
                        setIcon(AllIcons.General.GearPlain);
                    }
                } else if (userObject instanceof GHGist gist) {
                    Date updatedAt;
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
                    append(gist.isPublic() ? " [ Public ]" : " [ Private]");
                    setIcon(gist.isPublic() ? AllIcons.Nodes.C_public: AllIcons.Nodes.C_private);
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
            super(new DefaultMutableTreeNode("", true));
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
                for (String category : gistsMap.keySet()) {
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
            } finally {
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

        gistsTreeModel = new GistsTreeModel();
        gistsTree = new Tree(gistsTreeModel);

        setContent(ScrollPaneFactory.createScrollPane(gistsTree));

        TreeUIHelper.getInstance().installTreeSpeedSearch(gistsTree,
                (TreePath o) -> { return o.getLastPathComponent().toString(); },
                true);

        final ActionManager actionManager = ActionManager.getInstance();

        ToolWindowEx gistDoItToolWindowEx = (ToolWindowEx) ToolWindowManager.getInstance(project).getToolWindow("Gist Do It");

        ConnectToGithubAction connectToGithubAction = (ConnectToGithubAction) actionManager.getAction("ConnectToGithub");
        connectToGithubAction.setGistDoItToolWindow(this);


        GistsOfUser gistsOfUser = (GistsOfUser) actionManager.getAction("GistsOfUser");
        gistsOfUser.setGistDoItToolWindow(this);

        RefreshGistsAction refreshGistsAction = (RefreshGistsAction) actionManager.getAction("RefreshGists");
        refreshGistsAction.setGistDoItToolWindow(this);

        DisconnectFromGithubAction disconnectFromGithubAction = (DisconnectFromGithubAction) actionManager.getAction("DisconnectFromGithub");
        disconnectFromGithubAction.setGistDoItToolWindow(this);

        Objects.requireNonNull(gistDoItToolWindowEx).setTitleActions(java.util.List.of(connectToGithubAction, gistsOfUser, refreshGistsAction, disconnectFromGithubAction));

        gistsTree.setCellRenderer(new GistTreeCellRenderer());

        gistsTree.setRootVisible(true);

        gistsTree.getSelectionModel().setSelectionMode(DefaultTreeSelectionModel.SINGLE_TREE_SELECTION);

        gistsTree.addTreeSelectionListener(tse -> {
            Object lastPathComponent = tse.getPath().getLastPathComponent();
            if (lastPathComponent instanceof DefaultMutableTreeNode defaultMutableTreeNode) {
                Object userObject = defaultMutableTreeNode.getUserObject();
                if (userObject instanceof GHGistFile gistFile) {
                    String gistFileName = gistFile.getFileName();
                    String gistFileRawUrl = gistFile.getRawUrl();
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        gistsTree.setPaintBusy(true);
                        try {
                            String content = IOUtils.toString(new URI(gistFileRawUrl).toURL(), StandardCharsets.UTF_8);
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
                                lightVirtualFile.setLanguage(Objects.requireNonNullElse(language, PlainTextLanguage.INSTANCE));
                                FileEditorManager.getInstance(project).openFile(lightVirtualFile, true);
                            });
                        } catch (IOException | URISyntaxException e) {
                            throw new RuntimeException(e);
                        } finally {
                            gistsTree.setPaintBusy(false);
                        }
                    });
                }
            }
        });

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
        Timer timer = new Timer(1000, (ActionEvent e) -> {
            ensureGithubConnection(githubToken);
            loadGists();
        });
        timer.setRepeats(false);
        timer.start();
    }

    boolean isConnected() {
        return github != null;
    }

    void connectToGithub() {
        JPanel panel = new JPanel();
        JLabel label = new JLabel("Enter a GITHUB_TOKEN:");
        JPasswordField githubTokenPasswordField = new JPasswordField(42);
        panel.add(label);
        panel.add(githubTokenPasswordField);
        String[] options = new String[]{"Connect", "Cancel"};
        int option = JOptionPane.showOptionDialog(
                getTopLevelAncestor(),
                panel,
                "Enter GITHUB_TOKEN",
                DEFAULT_OPTION,
                QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (option == 0) {
            GitHub gitHub = ensureGithubConnection(new String(githubTokenPasswordField.getPassword()));
            if (gitHub != null) {
                loadGists();
            }
        }
    }

    private GitHub ensureGithubConnection(String githubToken) {
        if (github == null) {
            try {
                github = new GitHubBuilder().withOAuthToken(githubToken).build();
                githubUser = github.getMyself();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return github;
    }

    void loadGists() {
        if (github == null) {
            Notification notification = new Notification("gistDoItNotificationGroup",
                    "Not connected to Github",
                    "Please connect to github first.",
                    NotificationType.ERROR);
            notification.notify(project);
            return;
        }
        Notification loadingGistsNotification = new Notification("gistDoItNotificationGroup",
                "Loading gists....",
                "Loading gists. This may take some time.",
                NotificationType.INFORMATION);
        loadingGistsNotification.notify(project);

        gistsTree.setPaintBusy(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ApplicationManager.getApplication().runReadAction(() -> {
                try {
                    try {
                        // Clear
                        SwingUtilities.invokeAndWait(() -> {
                            ((GistsTreeModel) gistsTree.getModel()).setGists(Collections.emptySet());
                        });
                    } catch (InvocationTargetException | InterruptedException ignore) {
                    }
                    Set<GHGist> gists =githubUser.listGists().toSet();
                    SwingUtilities.invokeLater(() -> {
                        ((GistsTreeModel) gistsTree.getModel()).setGists(gists);
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    setCursor(null);
                    gistsTree.setPaintBusy(false);
                    loadingGistsNotification.expire();
                }
            });
        });
    }

    void loadGistsOfUser() {
        JPanel panel = new JPanel();
        JLabel label = new JLabel("Load gists of user ID:");
        JTextField githubUserId = new JTextField(42);
        githubUserId.setToolTipText("Blank indicates logged in user. Only public gists of other users are shown.");
        panel.add(label);
        panel.add(githubUserId);
        String[] options = new String[]{"Load Gists", "Cancel"};
        int option = JOptionPane.showOptionDialog(
                getTopLevelAncestor(),
                panel,
                "Gists for GITHUB user",
                DEFAULT_OPTION,
                QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (option == 0) {
            if (isConnected()) {
                try {
                    GHUser user;
                    String userId = githubUserId.getText().trim();
                    if (userId.isEmpty()) {
                        user = github.getMyself();;
                    } else {
                        user = github.getUser(userId);
                    }
                    githubUser = user;
                    loadGists();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        }

    }

    void disconnectFromGithub() {
        github = null;
        githubUser = null;
        gistsTreeModel.setGists(Collections.emptySet());
    }
}