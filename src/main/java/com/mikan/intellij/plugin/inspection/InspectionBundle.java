package com.mikan.intellij.plugin.inspection;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author mikan
 * @date 2024-07-28 16:33
 */
public final class InspectionBundle {

    @NonNls
    public static final String BUNDLE = "messages.InspectionBundle";

    private static final DynamicBundle INSTANCE = new DynamicBundle(InspectionBundle.class, BUNDLE);

    private InspectionBundle() {
    }

    public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
        Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }

}
