/*
 * This file is part of ViaBackwards - https://github.com/ViaVersion/ViaBackwards
 * Copyright (C) 2016-2023 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.viabackwards.api.data;

import com.google.common.base.Preconditions;
import com.viaversion.viabackwards.ViaBackwards;
import com.viaversion.viabackwards.api.BackwardsProtocol;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.data.MappingDataBase;
import com.viaversion.viaversion.api.data.Mappings;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.libs.fastutil.ints.Int2ObjectMap;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.util.Key;
import java.util.Map;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

public class BackwardsMappings extends MappingDataBase {

    private final Class<? extends Protocol<?, ?, ?, ?>> vvProtocolClass;
    protected Int2ObjectMap<MappedItem> backwardsItemMappings;
    private Map<String, String> backwardsSoundMappings;
    private Map<String, String> entityNames;

    public BackwardsMappings(String oldVersion, String newVersion, @Nullable Class<? extends Protocol<?, ?, ?, ?>> vvProtocolClass) {
        this(oldVersion, newVersion, vvProtocolClass, false);
    }

    public BackwardsMappings(String oldVersion, String newVersion, @Nullable Class<? extends Protocol<?, ?, ?, ?>> vvProtocolClass, boolean hasDiffFile) {
        super(oldVersion, newVersion, hasDiffFile);
        Preconditions.checkArgument(vvProtocolClass == null || !vvProtocolClass.isAssignableFrom(BackwardsProtocol.class));
        this.vvProtocolClass = vvProtocolClass;
        // Just re-use ViaVersion's item id map
    }

    @Override
    protected final void loadExtras(JsonObject unmappedIdentifiers, JsonObject mappedIdentifiers, @Nullable JsonObject diffMappings) {
        if (diffMappings != null) {
            JsonObject diffItems = diffMappings.getAsJsonObject("items");
            if (diffItems != null && mappedIdentifiers.get("items").isJsonArray() && unmappedIdentifiers.get("items").isJsonArray()) {
                backwardsItemMappings = VBMappingDataLoader.loadItemMappings(unmappedIdentifiers.getAsJsonArray("items"),
                        mappedIdentifiers.getAsJsonArray("items"), diffItems, shouldWarnOnMissing("items"));
            }

            JsonObject diffSounds = diffMappings.getAsJsonObject("sounds");
            if (diffSounds != null) {
                backwardsSoundMappings = VBMappingDataLoader.objectToMap(diffSounds);
            }

            JsonObject diffEntityNames = diffMappings.getAsJsonObject("entitynames");
            if (diffEntityNames != null) {
                entityNames = VBMappingDataLoader.objectToMap(diffEntityNames);
            }
        }

        // Just re-use ViaVersion's item id map
        if (vvProtocolClass != null) {
            itemMappings = Via.getManager().getProtocolManager().getProtocol(vvProtocolClass).getMappingData().getItemMappings().inverse();
        }

        loadVBExtras(unmappedIdentifiers, mappedIdentifiers, diffMappings);
    }

    @Override
    protected @Nullable Mappings loadFromArray(JsonObject unmappedIdentifiers, JsonObject mappedIdentifiers, @Nullable JsonObject diffMappings, String key) {
        if (!unmappedIdentifiers.has(key) || !mappedIdentifiers.has(key) || !shouldLoad(key)) {
            return null;
        }

        JsonObject diff = diffMappings != null ? diffMappings.getAsJsonObject(key) : null;
        return VBMappings.vbBuilder().unmapped(unmappedIdentifiers.getAsJsonArray(key)).mapped(mappedIdentifiers.getAsJsonArray(key))
                .diffMappings(diff).warnOnMissing(shouldWarnOnMissing(key)).build();
    }

    @Override
    protected @Nullable Mappings loadFromObject(JsonObject unmappedIdentifiers, JsonObject mappedIdentifiers, @Nullable JsonObject diffMappings, String key) {
        if (!unmappedIdentifiers.has(key) || !mappedIdentifiers.has(key) || !shouldLoad(key)) {
            return null;
        }

        JsonObject diff = diffMappings != null ? diffMappings.getAsJsonObject(key) : null;
        return VBMappings.vbBuilder().unmapped(unmappedIdentifiers.getAsJsonObject(key)).mapped(mappedIdentifiers.getAsJsonObject(key))
                .diffMappings(diff).warnOnMissing(shouldWarnOnMissing(key)).build();
    }

    @Override
    protected JsonObject loadDiffFile() {
        return VBMappingDataLoader.loadFromDataDir("mapping-" + newVersion + "to" + oldVersion + ".json");
    }

    /**
     * To be overridden.
     */
    protected void loadVBExtras(JsonObject unmappedIdentifiers, JsonObject mappedIdentifiers, JsonObject diffMappings) {
    }

    protected boolean shouldWarnOnMissing(String key) {
        return !key.equals("blocks") && !key.equals("statistics") && !key.equals("entities");
    }

    @Override
    protected boolean shouldLoad(final String key) {
        return !key.equals("items");
    }

    @Override
    protected Logger getLogger() {
        return ViaBackwards.getPlatform().getLogger();
    }

    /**
     * @see #getMappedItem(int) for custom backwards mappings
     */
    @Override
    public int getNewItemId(int id) {
        // Don't warn on missing here
        return this.itemMappings.getNewId(id);
    }

    @Override
    public int getNewBlockId(int id) {
        // Don't warn on missing here
        return this.blockMappings.getNewId(id);
    }

    @Override
    public int getOldItemId(final int id) {
        // Warn on missing
        return checkValidity(id, this.itemMappings.inverse().getNewId(id), "item");
    }

    public @Nullable MappedItem getMappedItem(int id) {
        return backwardsItemMappings != null ? backwardsItemMappings.get(id) : null;
    }

    public @Nullable String getMappedNamedSound(String id) {
        if (backwardsSoundMappings == null) {
            return null;
        }
        return backwardsSoundMappings.get(Key.stripMinecraftNamespace(id));
    }

    public @Nullable String mappedEntityName(String entityName) {
        if (entityNames == null) {
            ViaBackwards.getPlatform().getLogger().severe("No entity mappings found when requesting them for " + entityName);
            new Exception().printStackTrace();
            return null;
        }
        return entityNames.get(entityName);
    }

    public @Nullable Int2ObjectMap<MappedItem> getBackwardsItemMappings() {
        return backwardsItemMappings;
    }

    public @Nullable Map<String, String> getBackwardsSoundMappings() {
        return backwardsSoundMappings;
    }

    public @Nullable Class<? extends Protocol<?, ?, ?, ?>> getViaVersionProtocolClass() {
        return vvProtocolClass;
    }
}
