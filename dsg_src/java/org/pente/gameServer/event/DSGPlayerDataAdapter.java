package org.pente.gameServer.event;

import com.google.gson.*;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;

import java.awt.*;
import java.lang.reflect.Type;

public class DSGPlayerDataAdapter implements JsonDeserializer<DSGPlayerData>, JsonSerializer<DSGPlayerData> {

    @Override
    public DSGPlayerData deserialize(JsonElement json, Type typeOfT,
                                     JsonDeserializationContext context) throws JsonParseException {

        return context.deserialize(json, SimpleDSGPlayerData.class);
    }

    @Override
    public JsonElement serialize(DSGPlayerData dsgPlayerData, Type type, JsonSerializationContext jsonSerializationContext) {
        return jsonSerializationContext.serialize(dsgPlayerData);
    }
}
