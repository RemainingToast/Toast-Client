package dev.toastmc.client.gui.auth;

import com.mojang.authlib.exceptions.InvalidCredentialsException;
import dev.toastmc.client.util.LoginUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.Session;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.Objects;

public class AuthScreen extends Screen
{
    private final Screen parentScreen;

    private TextFieldWidget usernameField, passwordField;
    private ButtonWidget loginButton, cancelButton;
    private Text greeting, message;
    private String lastUsername;

    public AuthScreen(Screen parentScreen)
    {
        super(new TranslatableText("Authentication"));
        this.parentScreen = parentScreen;
        this.client = MinecraftClient.getInstance();
        lastUsername = Objects.requireNonNull(LoginUtil.INSTANCE.getSession()).getUsername();
        greeting = getGreeting(lastUsername);
    }

    @Override
    protected void init()
    {
        super.init();
        assert this.client != null;
        this.client.keyboard.setRepeatEvents(true);

        // Username Text Field
        usernameField = new TextFieldWidget(this.client.textRenderer,
                width / 2 - 100,
                76,
                200,
                20,
                new TranslatableText("Username / Email"));
        usernameField.setMaxLength(128);
        usernameField.setSuggestion(lastUsername); // Suggest their current username
        usernameField.setChangedListener(value -> {
            // Clear username suggestion if they're typing something else
            usernameField.setSuggestion(value.isEmpty() ? lastUsername : "");
            // Update the login button submission state
            loginButton.active = canSubmit();
        });
        children.add(usernameField);

        // Password Text Field
        passwordField = new PasswordFieldWidget(this.client.textRenderer,
                width / 2 - 100,
                116,
                200,
                20,
                new TranslatableText("Password"));
        passwordField.setChangedListener(value -> {
            // Tweak the login button depending on if password is given or not
            loginButton.setMessage(new TranslatableText((value.isEmpty() ? "Play Offline" : "Login")));
            loginButton.active = canSubmit();
            // Reset the cancel button accordingly (after a successful login)
            cancelButton.setMessage(new TranslatableText("Cancel"));
        });
        children.add(passwordField);

        // Login Button
        loginButton = new ButtonWidget(width / 2 - 100,
                height / 4 + 96 + 18,
                200,
                20,
                new TranslatableText("Play Offline"),
                button -> submit());
        loginButton.active = false;
        addButton(loginButton);

        // Cancel Button
        cancelButton = new ButtonWidget(width / 2 - 100,
                height / 4 + 120 + 18,
                200,
                20,
                new TranslatableText("Cancel"),
                button -> onClose());
        addButton(cancelButton);
    }

    @Override
    public boolean shouldCloseOnEsc()
    {
        return !usernameField.isFocused() && !passwordField.isFocused();
    }

    @Override
    public void onClose()
    {
        passwordField.setText("");
        this.client.openScreen(parentScreen);
    }

    @Override
    public void removed()
    {
        this.client.keyboard.setRepeatEvents(false);
    }

    /**
     * Sets the flash status message.
     *
     * @param message text component to show
     */
    public void setMessage(Text message)
    {
        this.message = message;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta)
    {
        renderBackground(matrices);

        assert this.client != null;
        drawCenteredText(matrices, this.client.textRenderer, title, width / 2, 17, 16777215);
        drawCenteredText(matrices, this.client.textRenderer, greeting, width / 2, 34, 16777215);

        if (message != null)
            drawCenteredText(matrices, this.client.textRenderer, message, width / 2, height / 4 + 86, 16777215);

        drawTextWithShadow(matrices,
                this.client.textRenderer,
                new TranslatableText("Username / Email"),
                width / 2 - 100,
                64,
                10526880);
        drawTextWithShadow(matrices,
                this.client.textRenderer,
                new TranslatableText("Password"),
                width / 2 - 100,
                104,
                10526880);

        usernameField.render(matrices, mouseX, mouseY, delta);
        passwordField.render(matrices, mouseX, mouseY, delta);

        super.render(matrices, mouseX, mouseY, delta);
    }

    /**
     * Determines if the current form can be submitted.
     *
     * @return true if the form is ready for submission
     */
    protected boolean canSubmit()
    {
        return !usernameField.getText().isEmpty() || !passwordField.getText().isEmpty();
    }

    /**
     * Submits the current form, logging the credentials in.
     */
    public void submit()
    {
        // Prevent pre-mature submissions
        if (!loginButton.active) return;
        loginButton.active = false; // disable login button while logging in

        final String username = usernameField.getText().isEmpty() ? lastUsername : usernameField.getText();
        final String password = passwordField.getText();

        if (password.isEmpty()) {
            // Play offline
            Session offlineSession = LoginUtil.INSTANCE.login(username);

            assert offlineSession != null;
            this.lastUsername = offlineSession.getUsername();
            this.greeting = getGreeting(lastUsername);
            this.message = new TranslatableText("Successfully playing offline!")
                    .styled(style -> style.withBold(true).withColor(Formatting.AQUA));

            // Reset form
            usernameField.setText("");
            passwordField.setText("");
            cancelButton.setMessage(new TranslatableText("Start Playing"));
        } else {
            // Login
            LoginUtil.INSTANCE.login(username, password)
                    .thenAccept(session -> {
                        // Successful login attempt
                        lastUsername = session.getUsername();
                        greeting = getGreeting(lastUsername);

                        // Set the message contents and style it as successful
                        message = new TranslatableText("Successfully logged in!")
                                .styled(style -> style.withBold(true).withColor(Formatting.GREEN));

                        // Reset form
                        usernameField.setText("");
                        passwordField.setText("");
                        cancelButton.setMessage(new TranslatableText("Start Playing"));
                    })
                    .exceptionally(e -> {
                        // Failed login attempt
                        loginButton.active = true; // re-enable login button to try again with same credentials

                        // Set the message contents and style it as an error
                        final TranslatableText text;
                        if (e.getCause() instanceof InvalidCredentialsException)
                            text = new TranslatableText("Invalid credentials!");
                        else
                            text = new TranslatableText("Unable to login!",
                                    e.getCause().getMessage());

                        this.message = text.styled(style -> style.withBold(true).withColor(Formatting.RED));

                        return null;
                    });
        }
    }

    /**
     * Formats and returns a greeting text component.
     *
     * @param username username in greeting
     * @return formatted translatable text component greeting
     */
    protected static Text getGreeting(String username)
    {
        return new TranslatableText("Logged in as %s", new LiteralText(username).styled(style -> style.withColor(Formatting.YELLOW))).styled(style -> style.withColor(Formatting.GRAY));
    }
}
