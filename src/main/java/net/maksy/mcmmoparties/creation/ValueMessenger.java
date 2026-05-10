package net.maksy.mcmmoparties.creation;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

import static net.maksy.mcmmoparties.configuration.enums.Lang.NOT_A_NUMBER;

public class ValueMessenger {
    private static ValueMessenger instance;

    public static ValueMessenger get() {
        return instance == null ? instance = new ValueMessenger() : instance;
    }

    private ValueMessenger() {
    }

    public void open(UUID uuid, int slot) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null)
            return;

        PartyEditor editor = EditorRegistry.getPartyEditor(uuid);
        openTextDialog(player, editor, slot);
    }

    public void setValue(UUID uuid, int pos, String text) {
        EditorRegistry.getPartyEditor(uuid).setValue(pos, text);
        EditorRegistry.getPartyEditor(uuid).open();
    }

    private void openTextDialog(Player player, PartyEditor editor, int slot) {
        final String key = "value";
        final String title = getTitle(slot);
        final String initial = getInitialText(editor, slot);
        final boolean requirementSlot = editor.isRequirementSlot(slot);
        final String prompt = requirementSlot
                ? "Enter a numeric requirement amount and press Save."
                : "Enter the new value and press Save.";
        final String inputTitle = requirementSlot ? "Requirement Amount" : "Value";

        Dialog dialog = Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(DialogBase.create(
                    Component.text(title),
                    null,
                    true,
                    false,
                    DialogBase.DialogAfterAction.CLOSE,
                    List.of(DialogBody.plainMessage(Component.text(prompt))),
                    List.of(DialogInput.text(key, 240, Component.text(inputTitle), true, initial, 256, null))
            ));
            builder.type(DialogType.notice(ActionButton.create(
                    Component.text("Save"),
                    null,
                    96,
                    DialogAction.customClick((response, audience) -> handleTextResponse(response, audience, editor, slot, key), ClickCallback.Options.builder().uses(1).build())
            )));
        });

        player.showDialog(dialog);
    }

    private void handleTextResponse(DialogResponseView response, Audience audience, PartyEditor editor, int slot, String key) {
        if (!(audience instanceof Player player))
            return;

        String value = response.getText(key);
        if (value == null)
            return;

        value = value.trim();
        if (editor.isRequirementSlot(slot) && !Utils.isNotNumber(value)) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_A_NUMBER));
            return;
        }

        editor.setValue(slot, value);
        player.closeDialog();
        editor.open();
    }

    private String getTitle(int slot) {
        switch (slot) {
            case 47:
                return "Edit Party ID";
            case 48:
                return "Edit Party Display Name";
            case 50:
                return "Edit Party Password";
            default:
                return "Edit Skill Requirement";
        }
    }

    private String getTitle(int slot, String requirementName, int currentAmount) {
        if (slot >= 47 && slot <= 50) {
            return getTitle(slot);
        }
        return "Edit " + requirementName + " Requirement (Current: " + currentAmount + ")";
    }

    private String getRequirementName(PartyEditor editor, int slot) {
        if (editor.getRequirement(slot) == null || editor.getRequirement(slot).getSkill() == null) {
            return "Skill";
        }
        return editor.getRequirement(slot).getSkill().name();
    }

    private String getInitialText(PartyEditor editor, int slot) {
        switch (slot) {
            case 47:
                return editor.getPartyID();
            case 48:
                return editor.getDisplay();
            case 50:
                return editor.getPassword();
            default:
                return editor.isRequirementSlot(slot) ? String.valueOf(editor.getRequirementAmount(slot)) : "";
        }
    }

}
