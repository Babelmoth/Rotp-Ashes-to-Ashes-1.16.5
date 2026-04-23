package com.babelmoth.rotp_ata.power;

import com.github.standobyte.jojo.power.impl.stand.stats.StandStats;
import com.github.standobyte.jojo.power.impl.stand.type.EntityStandType;

public class DharmaChakraStandType<T extends StandStats> extends EntityStandType<T> {

    protected DharmaChakraStandType(EntityStandType.AbstractBuilder<?, T> builder) {
        super(builder);
    }

    public static class Builder<T extends StandStats> extends EntityStandType.AbstractBuilder<Builder<T>, T> {
        @Override
        protected Builder<T> getThis() {
            return this;
        }

        @Override
        public DharmaChakraStandType<T> build() {
            return new DharmaChakraStandType<>(this);
        }
    }
}
