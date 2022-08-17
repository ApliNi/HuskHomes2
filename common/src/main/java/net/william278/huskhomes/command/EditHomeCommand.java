package net.william278.huskhomes.command;

import de.themoep.minedown.MineDown;
import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.player.OnlineUser;
import net.william278.huskhomes.position.Home;
import net.william278.huskhomes.position.PositionMeta;
import net.william278.huskhomes.util.Permission;
import net.william278.huskhomes.util.RegexUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EditHomeCommand extends CommandBase implements TabCompletable, ConsoleExecutable {

    private final String[] EDIT_HOME_COMPLETIONS = {"rename", "description", "relocate", "privacy"};

    public EditHomeCommand(@NotNull HuskHomes implementor) {
        super("edithome", Permission.COMMAND_EDIT_HOME, implementor);
    }

    @Override
    public void onExecute(@NotNull OnlineUser onlineUser, @NotNull String[] args) {
        if (args.length >= 1) {
            final String homeName = args[0];
            final String editOperation = args.length >= 2 ? args[1] : null;
            final String editArgs = getEditArguments(args);

            RegexUtil.matchDisambiguatedHomeIdentifier(homeName).ifPresentOrElse(
                    homeIdentifier -> plugin.getDatabase().getUserDataByName(homeIdentifier.ownerName())
                            .thenAccept(optionalUserData -> optionalUserData.ifPresentOrElse(userData -> {
                                        if (!userData.getUserUuid().equals(onlineUser.uuid)) {
                                            if (!onlineUser.hasPermission(Permission.COMMAND_EDIT_HOME_OTHER.node)) {
                                                plugin.getLocales().getLocale("error_no_permission")
                                                        .ifPresent(onlineUser::sendMessage);
                                                return;
                                            }
                                        }
                                        plugin.getDatabase().getHome(userData.user(), homeIdentifier.homeName()).thenAccept(optionalHome -> {
                                            if (optionalHome.isEmpty()) {
                                                plugin.getLocales().getLocale("error_home_invalid_other",
                                                                homeIdentifier.ownerName(), homeIdentifier.homeName())
                                                        .ifPresent(onlineUser::sendMessage);
                                                return;
                                            }
                                            editHome(optionalHome.get(), onlineUser, editOperation, editArgs);
                                        });

                                    },
                                    () -> plugin.getLocales().getLocale("error_home_invalid_other",
                                                    homeIdentifier.ownerName(), homeIdentifier.homeName())
                                            .ifPresent(onlineUser::sendMessage))),
                    () -> plugin.getDatabase().getHome(onlineUser, homeName).thenAccept(optionalHome -> {
                        if (optionalHome.isEmpty()) {
                            plugin.getLocales().getLocale("error_home_invalid", homeName)
                                    .ifPresent(onlineUser::sendMessage);
                            return;
                        }
                        editHome(optionalHome.get(), onlineUser, editOperation, editArgs);
                    })
            );
        } else {
            plugin.getLocales().getLocale("error_invalid_syntax",
                            "/edithome <name> [" + String.join("|", EDIT_HOME_COMPLETIONS) + "] [args]")
                    .ifPresent(onlineUser::sendMessage);
        }
    }

    /**
     * Perform the specified EditOperation on the specified home
     *
     * @param home          The home to edit
     * @param editor        The player who is editing the home
     * @param editOperation The edit operation to perform
     * @param editArgs      Arguments for the edit operation
     */
    private void editHome(@NotNull Home home, @NotNull OnlineUser editor,
                          @Nullable String editOperation, @Nullable String editArgs) {
        final AtomicBoolean showMenuFlag = new AtomicBoolean(false);
        final boolean otherOwner = !editor.uuid.equals(home.owner.uuid);

        if (editOperation == null) {
            getHomeEditorWindow(home, true, otherOwner,
                    !otherOwner || editor.hasPermission(Permission.COMMAND_HOME_OTHER.node))
                    .forEach(editor::sendMessage);
            return;
        }
        if (editArgs != null) {
            String argToCheck = editArgs;
            if (editArgs.contains(Pattern.quote(" "))) {
                argToCheck = editArgs.split(Pattern.quote(" "))[0];
            }
            if (argToCheck.equals("-m")) {
                showMenuFlag.set(true);
                editArgs = editArgs.replaceFirst("-m", "");
            }
        }

        switch (editOperation.toLowerCase()) {
            case "rename" -> {
                if (editArgs == null || editArgs.contains(Pattern.quote(" "))) {
                    plugin.getLocales().getLocale("error_invalid_syntax",
                                    "/edithome <name> rename <new name>")
                            .ifPresent(editor::sendMessage);
                    return;
                }

                final String oldHomeName = home.meta.name;
                final String newHomeName = editArgs;
                plugin.getSavedPositionManager().updateHomeMeta(home, new PositionMeta(newHomeName, home.meta.description))
                        .thenAccept(renameResult -> (switch (renameResult.resultType()) {
                            case SUCCESS -> {
                                if (home.owner.uuid.equals(editor.uuid)) {
                                    yield plugin.getLocales().getLocale("edit_home_update_name",
                                            oldHomeName, newHomeName);
                                } else {
                                    yield plugin.getLocales().getLocale("edit_home_update_name_other",
                                            home.owner.username, oldHomeName, newHomeName);
                                }
                            }
                            case FAILED_DUPLICATE -> plugin.getLocales().getLocale("error_home_name_taken");
                            case FAILED_NAME_LENGTH -> plugin.getLocales().getLocale("error_home_name_length");
                            case FAILED_NAME_CHARACTERS -> plugin.getLocales().getLocale("error_home_name_characters");
                            default -> Optional.of(new MineDown(""));
                        }).ifPresent(editor::sendMessage));
            }
            case "description" -> {
                if (editArgs == null) {
                    plugin.getLocales().getLocale("error_invalid_syntax",
                                    "/edithome <name> description <new description>")
                            .ifPresent(editor::sendMessage);
                    return;
                }

                final String oldHomeDescription = home.meta.description;
                final String newDescription = editArgs;
                plugin.getSavedPositionManager().updateHomeMeta(home, new PositionMeta(home.meta.name, newDescription))
                        .thenAccept(descriptionUpdateResult -> (switch (descriptionUpdateResult.resultType()) {
                            case SUCCESS -> {
                                if (home.owner.uuid.equals(editor.uuid)) {
                                    yield plugin.getLocales().getLocale("edit_home_update_description",
                                            home.meta.name,
                                            oldHomeDescription, newDescription);
                                } else {
                                    yield plugin.getLocales().getLocale("edit_home_update_description_other",
                                            home.owner.username, home.meta.name,
                                            oldHomeDescription, newDescription);
                                }
                            }
                            case FAILED_DESCRIPTION_LENGTH ->
                                    plugin.getLocales().getLocale("error_home_description_length");
                            case FAILED_DESCRIPTION_CHARACTERS ->
                                    plugin.getLocales().getLocale("error_home_description_characters");
                            default -> Optional.of(new MineDown(""));
                        }).ifPresent(editor::sendMessage));
            }
            case "relocate" -> editor.getPosition().thenAccept(position ->
                    plugin.getSavedPositionManager().updateHomePosition(home, position).thenRun(() -> {
                        if (home.owner.uuid.equals(editor.uuid)) {
                            editor.sendMessage(plugin.getLocales().getLocale("edit_home_update_location",
                                    home.meta.name).orElse(new MineDown("")));
                        } else {
                            editor.sendMessage(plugin.getLocales().getLocale("edit_home_update_location_other",
                                    home.owner.username, home.meta.name).orElse(new MineDown("")));
                        }

                        // Show the menu if the menu flag is set
                        if (showMenuFlag.get()) {
                            getHomeEditorWindow(home, false, otherOwner,
                                    !otherOwner || editor.hasPermission(Permission.COMMAND_HOME_OTHER.node))
                                    .forEach(editor::sendMessage);
                        }
                    }));
            case "privacy" -> {
                boolean newIsPublic = !home.isPublic;
                if (editArgs != null && !editArgs.isBlank()) {
                    if (editArgs.equalsIgnoreCase("private")) {
                        newIsPublic = false;
                    } else if (editArgs.equalsIgnoreCase("public")) {
                        newIsPublic = true;
                    } else {
                        plugin.getLocales().getLocale("error_invalid_syntax",
                                        "/edithome <name> privacy [private|public]")
                                .ifPresent(editor::sendMessage);
                        return;
                    }
                }
                final String privacyKeyedString = newIsPublic ? "public" : "private";

                if (newIsPublic == home.isPublic) {
                    plugin.getLocales().getLocale(
                                    "error_edit_home_privacy_already_" + privacyKeyedString)
                            .ifPresent(editor::sendMessage);
                    return;
                }

                //todo economy check here

                plugin.getSavedPositionManager().updateHomePrivacy(home, newIsPublic).thenRun(() -> {
                    if (home.owner.uuid.equals(editor.uuid)) {
                        editor.sendMessage(plugin.getLocales().getLocale(
                                "edit_home_privacy_" + privacyKeyedString + "_success",
                                home.meta.name).orElse(new MineDown("")));
                    } else {
                        editor.sendMessage(plugin.getLocales().getLocale(
                                "edit_home_privacy_" + privacyKeyedString + "_success_other",
                                home.owner.username, home.meta.name).orElse(new MineDown("")));
                    }

                    // Show the menu if the menu flag is set
                    if (showMenuFlag.get()) {
                        getHomeEditorWindow(home, false, otherOwner,
                                !otherOwner || editor.hasPermission(Permission.COMMAND_HOME_OTHER.node))
                                .forEach(editor::sendMessage);
                    }
                });
            }
            default -> plugin.getLocales().getLocale("error_invalid_syntax",
                            "/edithome <name> [" + String.join("|", EDIT_HOME_COMPLETIONS) + "] [args]")
                    .ifPresent(editor::sendMessage);
        }
    }

    @Nullable
    private String getEditArguments(@NotNull String[] args) {
        if (args.length > 2) {
            final StringJoiner joiner = new StringJoiner(" ");
            for (int i = 2; i < args.length; i++) {
                joiner.add(args[i]);
            }
            return joiner.toString();
        }
        return null;
    }

    /**
     * Get a formatted home editor chat window for a supplied {@link Home}
     *
     * @param home               The home to display
     * @param showTitle          Whether to show the menu title
     * @param otherViewer        If the viewer of the editor is not the homeowner
     * @param showTeleportButton Whether to show the teleport "use" button
     * @return List of {@link MineDown} messages to send to the editor that form the menu
     */
    @NotNull
    private List<MineDown> getHomeEditorWindow(@NotNull Home home, final boolean showTitle,
                                               final boolean otherViewer, final boolean showTeleportButton) {
        return new ArrayList<>() {{
            if (showTitle) {
                if (!otherViewer) {
                    plugin.getLocales().getLocale("edit_home_menu_title", home.meta.name)
                            .ifPresent(this::add);
                } else {
                    plugin.getLocales().getLocale("edit_home_menu_title_other", home.owner.username, home.meta.name)
                            .ifPresent(this::add);
                }
            }

            plugin.getLocales().getLocale("edit_home_menu_metadata_" + (!home.isPublic ? "private" : "public"),
                            DateTimeFormatter.ofPattern("MMM dd yyyy, HH:mm")
                                    .format(home.meta.creationTime.atZone(ZoneId.systemDefault())),
                            home.uuid.toString().split(Pattern.quote("-"))[0],
                            home.uuid.toString())
                    .ifPresent(this::add);

            if (home.meta.description.length() > 0) {
                final String escapedDescription = MineDown.escape(home.meta.description
                        .replaceAll(Pattern.quote("("), "\\(")
                        .replaceAll(Pattern.quote(")"), "\\)"));
                plugin.getLocales().getLocale("edit_home_menu_description",
                                escapedDescription.length() > 50
                                        ? escapedDescription.substring(0, 49).trim() + "…" : escapedDescription,
                                String.join("\n", escapedDescription.split("(?<=\\G.{40,}\\s)")))
                        .ifPresent(this::add);
            }

            if (!plugin.getSettings().crossServer) {
                plugin.getLocales().getLocale("edit_home_menu_world", home.world.name).ifPresent(this::add);
            } else {
                plugin.getLocales().getLocale("edit_home_menu_world_server", home.world.name, home.server.name).ifPresent(this::add);
            }

            plugin.getLocales().getLocale("edit_home_menu_coordinates",
                            String.format("%.1f", home.x), String.format("%.1f", home.y), String.format("%.1f", home.z),
                            String.format("%.2f", home.yaw), String.format("%.2f", home.pitch))
                    .ifPresent(this::add);

            final String formattedName = home.owner.username + "." + home.meta.name;
            if (showTeleportButton) {
                plugin.getLocales().getLocale("edit_home_menu_use_buttons",
                                formattedName)
                        .ifPresent(this::add);
            }
            plugin.getLocales().getLocale("edit_home_menu_manage_buttons",
                            formattedName)
                    .ifPresent(this::add);
            plugin.getLocales().getLocale("edit_home_menu_meta_edit_buttons",
                            formattedName)
                    .ifPresent(this::add);
        }};
    }

    @Override
    public void onConsoleExecute(@NotNull String[] args) {
        //todo
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull OnlineUser onlineUser, @NotNull String[] args) {
        return switch (args.length) {
            case 0, 1 -> plugin.getCache().homes.get(onlineUser.uuid).stream()
                    .filter(s -> s.toLowerCase().startsWith(args.length == 1 ? args[0].toLowerCase() : ""))
                    .sorted().collect(Collectors.toList());
            case 2 -> Arrays.stream(EDIT_HOME_COMPLETIONS)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .sorted().collect(Collectors.toList());
            default -> Collections.emptyList();
        };
    }
}
