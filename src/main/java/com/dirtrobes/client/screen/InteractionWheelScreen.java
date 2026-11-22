package com.dirtrobes.client.screen;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.interaction.ClientConfig;
import com.dirtrobes.interaction.SpectrobeActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Roue d'interaction (Shift + clic droit sur son Spectrobe) : un anneau d'actions autour du viseur.
 * Survoler une case puis cliquer ; Échap ou clic au centre pour fermer.
 * Icônes provisoires : assets/dirtrobes/textures/gui/wheel/*.png (générées par tools/models/wheel_icons.py).
 */
public class InteractionWheelScreen extends Screen {
    private static final int RADIUS = 62;
    private static final int BOX = 28;
    private static final int DEAD_ZONE = 18;

    private record Slot(int action, String icon, Component label, boolean enabled, Component reason) {}

    private final SpectrobeEntity target;
    private final List<Slot> slots = new ArrayList<>();
    private int hovered = -1;

    private InteractionWheelScreen(SpectrobeEntity target) {
        super(Component.translatable("gui.dirtrobes.wheel.title"));
        this.target = target;
    }

    /** Appelé par le clic sur le Spectrobe : la roue, ou l'ancien « Reste ici / Suis-moi » si elle est désactivée. */
    public static void open(SpectrobeEntity target) {
        if (!ClientConfig.wheelEnabled()) {
            PacketDistributor.sendToServer(new SpectrobeActions.ActionPayload(target.getId(), SpectrobeActions.STAY_FOLLOW));
            return;
        }
        Minecraft.getInstance().setScreen(new InteractionWheelScreen(target));
    }

    /** Spectrobe porté sur l'épaule du joueur local, ou null. */
    @org.jetbrains.annotations.Nullable
    private static SpectrobeEntity shoulderSpectrobe(net.minecraft.world.entity.player.Player player) {
        for (var e : player.getPassengers()) if (e instanceof SpectrobeEntity s && s.isOwnedBy(player)) return s;
        return null;
    }

    /** Shift + clic droit main vide dans le vide : la roue du Spectrobe sur l'épaule (on ne peut pas le viser). */
    public static void onRightClickEmpty(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickEmpty event) {
        var player = event.getEntity();
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return;
        SpectrobeEntity s = shoulderSpectrobe(player);
        if (s != null) open(s);
    }

    /** Pareil en visant un bloc (sinon on poserait / utiliserait le bloc). */
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (!event.getLevel().isClientSide || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return;
        SpectrobeEntity s = shoulderSpectrobe(player);
        if (s == null) return;
        open(s);
        event.setCanceled(true);
    }

    /** Réponse du serveur à « Fiche ». */
    public static void openSummary(int slot) {
        Minecraft.getInstance().setScreen(new SpectrobeSummaryScreen(null, slot));
    }

    @Override
    protected void init() {
        slots.clear();
        Component none = Component.empty();
        boolean sitting = target.isOrderedToSit();
        slots.add(new Slot(SpectrobeActions.STAY_FOLLOW, sitting ? "follow" : "stay",
                Component.translatable(sitting ? "gui.dirtrobes.wheel.follow" : "gui.dirtrobes.wheel.stay"), true, none));
        if (target.canBeRidden()) {
            boolean riding = minecraft != null && minecraft.player != null && minecraft.player.getVehicle() == target;
            slots.add(new Slot(SpectrobeActions.RIDE, riding ? "dismount" : "ride",
                    Component.translatable(riding ? "gui.dirtrobes.wheel.dismount" : "gui.dirtrobes.wheel.ride"),
                    !target.isKnockedOut(), Component.translatable("gui.dirtrobes.wheel.knocked_out")));
        } else {
            boolean onShoulder = target.isOnShoulder();
            slots.add(new Slot(SpectrobeActions.SHOULDER, onShoulder ? "dismount" : "shoulder",
                    Component.translatable(onShoulder ? "gui.dirtrobes.wheel.off_shoulder" : "gui.dirtrobes.wheel.shoulder"),
                    target.canSitOnShoulder(), Component.translatable("gui.dirtrobes.wheel.shoulder_children_only")));
        }
        slots.add(new Slot(SpectrobeActions.SUMMARY, "summary", Component.translatable("gui.dirtrobes.wheel.summary"), true, none));
        slots.add(new Slot(SpectrobeActions.RECALL, "recall", Component.translatable("gui.dirtrobes.wheel.recall"), true, none));
        slots.add(new Slot(SpectrobeActions.TO_PC, "pc", Component.translatable("gui.dirtrobes.wheel.to_pc"), true, none));
        if (SpectrobeActions.cobblemonLoaded()) {
            slots.add(new Slot(SpectrobeActions.TO_COBBLEMON, "cobblemon", Component.translatable("gui.dirtrobes.wheel.to_cobblemon"), true, none));
        }
    }

    private int slotX(int i) {
        return width / 2 + Math.round(Mth.sin(angle(i)) * RADIUS);
    }

    private int slotY(int i) {
        return height / 2 - Math.round(Mth.cos(angle(i)) * RADIUS);
    }

    private float angle(int i) {
        return (float) (Math.PI * 2 * i / slots.size());
    }

    private int slotAt(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE || slots.isEmpty()) return -1;
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += Math.PI * 2;
        return (int) Math.round(a / (Math.PI * 2 / slots.size())) % slots.size();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!target.isAlive()) {
            onClose();
            return;
        }
        hovered = slotAt(mouseX, mouseY);
        int cx = width / 2, cy = height / 2;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            int x = slotX(i) - BOX / 2, y = slotY(i) - BOX / 2;
            boolean hover = i == hovered;
            int border = !s.enabled() ? 0xAA555555 : hover ? 0xFFFFE08A : 0xCC8FC7FF;
            int fill = !s.enabled() ? 0xAA1A1A1A : hover ? 0xEE3A4A66 : 0xCC1E2636;
            int grow = hover ? 2 : 0;
            g.fill(x - 1 - grow, y - 1 - grow, x + BOX + 1 + grow, y + BOX + 1 + grow, border);
            g.fill(x - grow, y - grow, x + BOX + grow, y + BOX + grow, fill);
            ResourceLocation icon = DirtRobes.id("textures/gui/wheel/" + s.icon() + ".png");
            if (!s.enabled()) g.setColor(0.45f, 0.45f, 0.45f, 1f);
            g.blit(icon, x + (BOX - 16) / 2, y + (BOX - 16) / 2, 0, 0, 16, 16, 16, 16);
            g.setColor(1f, 1f, 1f, 1f);
        }
        // centre : nom du Spectrobe ou action survolée
        g.fill(cx - 3, cy, cx + 4, cy + 1, 0x88FFFFFF);
        g.fill(cx, cy - 3, cx + 1, cy + 4, 0x88FFFFFF);
        Component title = hovered >= 0 ? slots.get(hovered).label() : target.getDisplayName();
        g.drawCenteredString(font, title, cx, cy + RADIUS + BOX / 2 + 8, 0xFFFFFF);
        if (hovered >= 0 && !slots.get(hovered).enabled()) {
            g.drawCenteredString(font, slots.get(hovered).reason(), cx, cy + RADIUS + BOX / 2 + 20, 0xAAAAAA);
        } else if (hovered < 0) {
            g.drawCenteredString(font, Component.translatable("gui.dirtrobes.wheel.hint"), cx, cy + RADIUS + BOX / 2 + 20, 0xAAAAAA);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // pas de flou ni d'assombrissement : on voit son Spectrobe derrière la roue
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = slotAt(mouseX, mouseY);
        if (i >= 0) {
            Slot s = slots.get(i);
            if (!s.enabled()) return true;
            PacketDistributor.sendToServer(new SpectrobeActions.ActionPayload(target.getId(), s.action()));
        }
        onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
