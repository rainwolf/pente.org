package org.pente.gameServer.event;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;

import java.lang.reflect.Type;

public class DSGPlayerDataAdapter implements JsonDeserializer<DSGPlayerData> {

    @Override
    public DSGPlayerData deserialize(JsonElement json, Type typeOfT,
                                     JsonDeserializationContext context) throws JsonParseException {

        return context.deserialize(json, SimpleDSGPlayerData.class);
    }
}
