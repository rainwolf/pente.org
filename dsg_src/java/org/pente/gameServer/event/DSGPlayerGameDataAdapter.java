package org.pente.gameServer.event;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.SimpleDSGPlayerGameData;

import java.lang.reflect.Type;

public class DSGPlayerGameDataAdapter implements JsonDeserializer<DSGPlayerGameData> {

    @Override
    public DSGPlayerGameData deserialize(JsonElement json, Type typeOfT,
                                         JsonDeserializationContext context) throws JsonParseException {

        return context.deserialize(json, SimpleDSGPlayerGameData.class);
    }
}
