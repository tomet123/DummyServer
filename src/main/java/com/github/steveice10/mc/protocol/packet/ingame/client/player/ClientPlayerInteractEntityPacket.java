package com.github.steveice10.mc.protocol.packet.ingame.client.player;

import com.github.steveice10.mc.protocol.data.MagicValues;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.packetlib.io.NetInput;
import com.github.steveice10.packetlib.io.NetOutput;
import com.github.steveice10.packetlib.packet.Packet;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.io.IOException;

@Data
@Setter(AccessLevel.NONE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class ClientPlayerInteractEntityPacket implements Packet {
    private int entityId;
    private @NonNull InteractAction action;

    private float targetX;
    private float targetY;
    private float targetZ;
    private @NonNull Hand hand;

    public ClientPlayerInteractEntityPacket(int entityId, InteractAction action) {
        this(entityId, action, Hand.MAIN_HAND);
    }

    public ClientPlayerInteractEntityPacket(int entityId, InteractAction action, Hand hand) {
        this(entityId, action, 0, 0, 0, hand);
    }

    @Override
    public void read(NetInput in) throws IOException {
        this.entityId = in.readVarInt();
        this.action = MagicValues.key(InteractAction.class, in.readVarInt());
        if(this.action == InteractAction.INTERACT_AT) {
            this.targetX = in.readFloat();
            this.targetY = in.readFloat();
            this.targetZ = in.readFloat();
        }

        if(this.action == InteractAction.INTERACT || this.action == InteractAction.INTERACT_AT) {
            this.hand = MagicValues.key(Hand.class, in.readVarInt());
        }
    }

    @Override
    public void write(NetOutput out) throws IOException {
        out.writeVarInt(this.entityId);
        out.writeVarInt(MagicValues.value(Integer.class, this.action));
        if(this.action == InteractAction.INTERACT_AT) {
            out.writeFloat(this.targetX);
            out.writeFloat(this.targetY);
            out.writeFloat(this.targetZ);
        }

        if(this.action == InteractAction.INTERACT || this.action == InteractAction.INTERACT_AT) {
            out.writeVarInt(MagicValues.value(Integer.class, this.hand));
        }
    }

    @Override
    public boolean isPriority() {
        return false;
    }
}
