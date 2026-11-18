package com.dirtrobes.combat;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.species.Property;
import com.dirtrobes.team.PrizmodTeam;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Spectrobes de soutien (M14) : chaque membre de l'équipe rangé (et pas K.O.) donne un petit bonus
 * aux Spectrobes dehors, selon sa propriété. Les bonus s'additionnent.
 * <ul>
 *   <li>Corona : +5 % ATQ · Aurora : +5 % DÉF · Flash : +5 % VIT</li>
 *   <li>Eclipse : +10 % de gain de jauge CH · Halo : +5 % PV max · Prisma : +2 % partout</li>
 * </ul>
 */
public record Support(float atk, float def, float spd, float ch, float hp) {
    public static final Support NONE = new Support(0, 0, 0, 0, 0);

    private static final ResourceLocation ATK_ID = DirtRobes.id("support_attack");
    private static final ResourceLocation DEF_ID = DirtRobes.id("support_defense");
    private static final ResourceLocation SPD_ID = DirtRobes.id("support_speed");
    private static final ResourceLocation HP_ID = DirtRobes.id("support_health");

    public boolean isEmpty() {
        return atk == 0 && def == 0 && spd == 0 && ch == 0 && hp == 0;
    }

    public static Support of(Property p) {
        return switch (p) {
            case CORONA -> new Support(0.05f, 0, 0, 0, 0);
            case AURORA -> new Support(0, 0.05f, 0, 0, 0);
            case FLASH -> new Support(0, 0, 0.05f, 0, 0);
            case ECLIPSE -> new Support(0, 0, 0, 0.10f, 0);
            case HALO -> new Support(0, 0, 0, 0, 0.05f);
            default -> new Support(0.02f, 0.02f, 0.02f, 0.02f, 0.02f);
        };
    }

    public Support plus(Support o) {
        return new Support(atk + o.atk, def + o.def, spd + o.spd, ch + o.ch, hp + o.hp);
    }

    /** Bonus apporté par les membres rangés de l'équipe du joueur. */
    public static Support compute(ServerPlayer player) {
        Support total = NONE;
        for (PrizmodTeam.Entry entry : PrizmodTeam.team(player).entries) {
            if (entry.isOut() || entry.data == null) continue;
            SpectrobeEntity s = ModRegistry.SPECTROBE.get().create(player.serverLevel());
            if (s == null) continue;
            s.load(entry.data);
            if (!s.isKnockedOut()) total = total.plus(of(s.getProperty()));
        }
        return total;
    }

    /** Applique (ou met à jour) les bonus sur un Spectrobe dehors, sous forme de modificateurs temporaires. */
    public void applyTo(SpectrobeEntity s) {
        apply(s, Attributes.ATTACK_DAMAGE, ATK_ID, atk);
        apply(s, Attributes.ARMOR, DEF_ID, def);
        apply(s, Attributes.MOVEMENT_SPEED, SPD_ID, spd);
        float oldMax = s.getMaxHealth();
        apply(s, Attributes.MAX_HEALTH, HP_ID, hp);
        if (s.getHealth() > s.getMaxHealth()) s.setHealth(s.getMaxHealth());
        else if (s.getMaxHealth() > oldMax) s.setHealth(s.getHealth() + (s.getMaxHealth() - oldMax));
    }

    private static void apply(SpectrobeEntity s, Holder<Attribute> attribute, ResourceLocation id, float amount) {
        AttributeInstance inst = s.getAttribute(attribute);
        if (inst == null) return;
        if (amount <= 0) {
            inst.removeModifier(id);
        } else {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    public static final StreamCodec<FriendlyByteBuf, Support> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeFloat(s.atk);
                buf.writeFloat(s.def);
                buf.writeFloat(s.spd);
                buf.writeFloat(s.ch);
                buf.writeFloat(s.hp);
            },
            buf -> new Support(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
}
