package org.pente.gameServer.event;

import java.awt.Color;
import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class DSGColorAdapter implements JsonDeserializer<Color>, JsonSerializer<Color> {

    @Override
    public Color deserialize(JsonElement json, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        if (!(json instanceof JsonObject)) {
            throw new JsonParseException("The color " + json + " is not an object!");
        }

        if (!json.getAsJsonObject().has("value")) {
            throw new JsonParseException("The color " + json + " has no value!");
        }

        Color color = new Color(json.getAsJsonObject().get("value").getAsInt());

        return color;
    }

    @Override
    public JsonElement serialize(Color value, Type type, JsonSerializationContext context) {
        JsonObject colorObject = new JsonObject();
        colorObject.add("value", new JsonPrimitive(value.getRGB()));
        colorObject.add("falpha", new JsonPrimitive(0.0f)); // seems to be no way to get this easily
        return colorObject;
    }
}