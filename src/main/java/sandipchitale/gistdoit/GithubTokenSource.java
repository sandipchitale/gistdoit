package sandipchitale.gistdoit;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies GitHub credentials to the plugin.
 * <p>
 * Deliberately free of any {@code org.jetbrains.plugins.github} types so that this class - and
 * everything in the main plugin module that references it - loads even when the bundled GitHub
 * plugin is disabled or absent. The native implementation ({@code IdeAccountsTokenSource}) is
 * registered from {@code META-INF/gistdoit-github.xml}, which the platform only loads when the
 * optional dependency on {@code org.jetbrains.plugins.github} is satisfied.
 */
public interface GithubTokenSource {
    ExtensionPointName<GithubTokenSource> EP_NAME =
            ExtensionPointName.create("sandipchitale.gistdoit.githubTokenSource");

    /**
     * @param apiUrl REST API endpoint, e.g. {@code https://api.github.com} or
     *               {@code https://ghe.example.com/api/v3}
     * @param login  the account login, may be {@code null} if unknown
     * @param token  the access token
     */
    record Credentials(@NotNull String apiUrl, @Nullable String login, @NotNull String token) {
    }

    /**
     * @return {@code true} if at least one account is already configured, so that
     * {@link #resolve(Project)} can be expected to succeed without asking the user to sign in
     */
    boolean hasConfiguredAccounts();

    /**
     * Resolves credentials, interacting with the user (login dialog, account chooser) if needed.
     * <p>
     * Called on a background thread - implementations may block.
     *
     * @return {@code null} if the user cancelled or no account could be obtained
     */
    @Nullable
    Credentials resolve(@NotNull Project project);
}
