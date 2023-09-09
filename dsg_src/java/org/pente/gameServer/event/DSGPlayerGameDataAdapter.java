package org.pente.gameServer.event;

import com.google.gson.*;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.SimpleDSGPlayerGameData;

import java.lang.reflect.Type;

public class DSGPlayerGameDataAdapter implements JsonDeserializer<DSGPlayerGameData>, JsonSerializer<DSGPlayerGameData> {

    @Override
    public DSGPlayerGameData deserialize(JsonElement json, Type typeOfT,
                                         JsonDeserializationContext context) throws JsonParseException {

        return context.deserialize(json, SimpleDSGPlayerGameData.class);
    }

    @Override
    public JsonElement serialize(DSGPlayerGameData dsgPlayerGameData, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject dsgPlayerGameDataObject = new JsonObject();

        dsgPlayerGameDataObject.add("playerID", new JsonPrimitive(dsgPlayerGameData.getPlayerID()));
        dsgPlayerGameDataObject.add("game", new JsonPrimitive(dsgPlayerGameData.getGame()));
        dsgPlayerGameDataObject.add("wins", new JsonPrimitive(dsgPlayerGameData.getWins()));
        dsgPlayerGameDataObject.add("losses", new JsonPrimitive(dsgPlayerGameData.getLosses()));
        dsgPlayerGameDataObject.add("draws", new JsonPrimitive(dsgPlayerGameData.getDraws()));
        dsgPlayerGameDataObject.add("rating", new JsonPrimitive(dsgPlayerGameData.getRating()));
        dsgPlayerGameDataObject.add("streak", new JsonPrimitive(dsgPlayerGameData.getStreak()));
        dsgPlayerGameDataObject.add("lastGameDate", new JsonPrimitive(dsgPlayerGameData.getLastGameDate().toString()));
        dsgPlayerGameDataObject.add("tourneyWinner", new JsonPrimitive(dsgPlayerGameData.getTourneyWinner()));
        dsgPlayerGameDataObject.add("computer", new JsonPrimitive(dsgPlayerGameData.getComputer()));
        return dsgPlayerGameDataObject;
    }
}
