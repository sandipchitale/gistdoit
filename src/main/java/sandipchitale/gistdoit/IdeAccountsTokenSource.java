package sandipchitale.gistdoit;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.authentication.GHAccountAuthData;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.GHLoginSource;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHCompatibilityUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Obtains the GitHub token from the IDE's own GitHub accounts - the ones managed under
 * <em>Settings | Version Control | GitHub</em> and stored in the {@code PasswordSafe}.
 * <p>
 * This is the only class in the plugin that references {@code org.jetbrains.plugins.github}. It is
 * registered from {@code META-INF/gistdoit-github.xml}, which the platform loads only when the
 * optional dependency on the bundled GitHub plugin is satisfied - so it is never class-loaded when
 * that plugin is disabled.
 */
public final class IdeAccountsTokenSource implements GithubTokenSource {

    @Override
    public boolean hasConfiguredAccounts() {
        return !GHAccountsUtil.getAccounts().isEmpty();
    }

    @Override
    public @Nullable Credentials resolve(@NotNull Project project) {
        GithubAccount account = GHAccountsUtil.getSingleOrDefaultAccount(project);
        if (account == null) {
            Set<GithubAccount> accounts = GHAccountsUtil.getAccounts();
            account = accounts.isEmpty() ? requestNewAccount(project) : chooseAccount(project, accounts);
            if (account == null) {
                // User cancelled the login or the account chooser.
                return null;
            }
        }
        // @RequiresBackgroundThread: getOrRequestToken() blocks and switches to the EDT itself to
        // show the login dialog when there is no stored token, so it must not be called on the EDT.
        String token = GHCompatibilityUtil.getOrRequestToken(account, project, GHLoginSource.GIST);
        if (token == null) {
            return null;
        }
        return new Credentials(account.getServer().toApiUrl(), account.getName(), token);
    }

    /**
     * Shows the IDE's own GitHub login dialog (browser OAuth or personal access token) and adds the
     * resulting account to Settings | Version Control | GitHub.
     */
    private static @Nullable GithubAccount requestNewAccount(@NotNull Project project) {
        Ref<GHAccountAuthData> authData = Ref.create();
        // @RequiresEdt
        ApplicationManager.getApplication().invokeAndWait(() -> authData.set(GHAccountsUtil.requestNewAccount(project)));
        GHAccountAuthData data = authData.get();
        return data == null ? null : data.getAccount();
    }

    /**
     * Asks which account to use when several are configured and none is the default for the
     * project, then remembers the answer as the project default so we do not ask again.
     */
    private static @Nullable GithubAccount chooseAccount(@NotNull Project project, @NotNull Set<GithubAccount> accounts) {
        List<GithubAccount> sorted = new ArrayList<>(accounts);
        sorted.sort(Comparator.comparing(GithubAccount::getName));

        Ref<GithubAccount> chosen = Ref.create();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            AccountChooserDialog dialog = new AccountChooserDialog(project, sorted);
            if (dialog.showAndGet()) {
                GithubAccount account = dialog.getSelectedAccount();
                chosen.set(account);
                GHAccountsUtil.setDefaultAccount(project, account);
            }
        });
        return chosen.get();
    }

    private static final class AccountChooserDialog extends DialogWrapper {
        private final ComboBox<GithubAccount> comboBox;

        private AccountChooserDialog(@NotNull Project project, @NotNull List<GithubAccount> accounts) {
            super(project, false);
            comboBox = new ComboBox<>(new CollectionComboBoxModel<>(accounts, accounts.get(0)));
            comboBox.setRenderer(new SimpleListCellRenderer<>() {
                @Override
                public void customize(@NotNull JList<? extends GithubAccount> list, GithubAccount account,
                                      int index, boolean selected, boolean hasFocus) {
                    if (account != null) {
                        setText(account.getName() + " (" + account.getServer().toUrl() + ")");
                    }
                }
            });
            setTitle("Gist Do It");
            init();
        }

        @Override
        protected @NotNull JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(4)));
            panel.add(new JLabel("GitHub account to load gists from:"), BorderLayout.NORTH);
            panel.add(comboBox, BorderLayout.CENTER);
            return panel;
        }

        @Override
        public @Nullable JComponent getPreferredFocusedComponent() {
            return comboBox;
        }

        private GithubAccount getSelectedAccount() {
            return (GithubAccount) comboBox.getSelectedItem();
        }
    }
}
