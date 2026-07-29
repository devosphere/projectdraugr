package com.devosphere.draugr.world.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorldObjectTest {
    @Test
    void activeObjectRequiresExactlyOneLocationOrOwner() {
        assertThrows(IllegalArgumentException.class, () -> new WorldObject("ITEM", "Stone", null, null));
        UUID location = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new WorldObject("ITEM", "Stone", location, owner));
    }

    @Test
    void objectCanMoveOrTransferButCannotChangeAfterDestruction() {
        WorldObject object = new WorldObject("ITEM", "Stone axe", UUID.randomUUID(), null);
        object.transferTo(UUID.randomUUID());
        object.moveTo(UUID.randomUUID());
        object.destroy();

        assertEquals(LifecycleState.DESTROYED, object.getLifecycleState());
        assertThrows(IllegalStateException.class, () -> object.transferTo(UUID.randomUUID()));
    }
}
