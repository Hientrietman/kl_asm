package com.prelude.klasm;

import java.util.UUID;

/** Fixed ids from V3__seed_reference_data.sql. */
public final class SeedData {

    public static final UUID LONDON = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    public static final UUID HO_CHI_MINH = UUID.fromString("d0000000-0000-0000-0000-000000000002");

    public static final UUID LONDON_BAY_1 = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    public static final UUID LONDON_BAY_2 = UUID.fromString("b1000000-0000-0000-0000-000000000002");
    public static final UUID LONDON_BAY_3 = UUID.fromString("b1000000-0000-0000-0000-000000000003");

    public static final UUID LONDON_TECH_1 = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    public static final UUID LONDON_TECH_2 = UUID.fromString("a1000000-0000-0000-0000-000000000002");

    public static final UUID OIL_CHANGE = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    public static final UUID TIRE_ROTATION = UUID.fromString("c0000000-0000-0000-0000-000000000002");
    public static final UUID BRAKE_SERVICE = UUID.fromString("c0000000-0000-0000-0000-000000000003");
    public static final UUID FULL_INSPECTION = UUID.fromString("c0000000-0000-0000-0000-000000000004");

    private SeedData() {}
}
