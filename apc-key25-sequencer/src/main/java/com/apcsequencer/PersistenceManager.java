package com.apcsequencer;

import com.google.gson.*;

public class PersistenceManager {

    public static String serialize(TrackState[] tracks, int scaleIndex,
                                   int rootNote, int activeTrack) {
        JsonObject root = new JsonObject();
        root.addProperty("scaleIndex",  scaleIndex);
        root.addProperty("rootNote",    rootNote);
        root.addProperty("activeTrack", activeTrack);

        JsonArray tracksArr = new JsonArray();
        for (TrackState t : tracks) {
            JsonObject obj = new JsonObject();
            obj.addProperty("patternLength", t.patternLength);
            obj.addProperty("melodicMode",   t.melodicMode);
            obj.addProperty("baseNote",      t.baseNote);
            obj.addProperty("muted",         t.muted);
            obj.add("steps",          boolArray(t.steps));
            obj.add("notes",          intArray(t.notes));
            obj.add("velocities",     intArray(t.velocities));
            obj.add("gateLengths",    doubleArray(t.gateLengths));
            obj.add("probabilities",  doubleArray(t.probabilities));
            obj.add("nudges",         intArray(t.nudges));
            obj.add("ratchets",       intArray(t.ratchets));
            obj.add("chordIntervals", intArray(t.chordIntervals));
            obj.add("ccValues",       intArray(t.ccValues));
            tracksArr.add(obj);
        }
        root.add("tracks", tracksArr);
        return new Gson().toJson(root);
    }

    public static void deserialize(String json, TrackState[] tracks,
                                   int[] scaleIndexOut, int[] rootNoteOut,
                                   int[] activeTrackOut) {
        if (json == null || json.isBlank()) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            scaleIndexOut[0]  = root.get("scaleIndex").getAsInt();
            rootNoteOut[0]    = root.get("rootNote").getAsInt();
            activeTrackOut[0] = root.get("activeTrack").getAsInt();

            JsonArray arr = root.getAsJsonArray("tracks");
            for (int i = 0; i < Math.min(tracks.length, arr.size()); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                TrackState t = tracks[i];
                t.patternLength = obj.get("patternLength").getAsInt();
                t.melodicMode   = obj.get("melodicMode").getAsBoolean();
                t.baseNote      = obj.get("baseNote").getAsInt();
                t.muted         = obj.get("muted").getAsBoolean();
                readBoolArr(obj.getAsJsonArray("steps"),          t.steps);
                readIntArr( obj.getAsJsonArray("notes"),          t.notes);
                readIntArr( obj.getAsJsonArray("velocities"),     t.velocities);
                readDblArr( obj.getAsJsonArray("gateLengths"),    t.gateLengths);
                readDblArr( obj.getAsJsonArray("probabilities"),  t.probabilities);
                readIntArr( obj.getAsJsonArray("nudges"),         t.nudges);
                readIntArr( obj.getAsJsonArray("ratchets"),       t.ratchets);
                readIntArr( obj.getAsJsonArray("chordIntervals"), t.chordIntervals);
                readIntArr( obj.getAsJsonArray("ccValues"),       t.ccValues);
            }
        } catch (Exception ignored) {
            // Corrupt JSON — leave tracks at default state
        }
    }

    private static JsonArray boolArray(boolean[] a) {
        JsonArray r = new JsonArray();
        for (boolean v : a) r.add(v);
        return r;
    }
    private static JsonArray intArray(int[] a) {
        JsonArray r = new JsonArray();
        for (int v : a) r.add(v);
        return r;
    }
    private static JsonArray doubleArray(double[] a) {
        JsonArray r = new JsonArray();
        for (double v : a) r.add(v);
        return r;
    }
    private static void readBoolArr(JsonArray a, boolean[] out) {
        for (int i = 0; i < Math.min(a.size(), out.length); i++)
            out[i] = a.get(i).getAsBoolean();
    }
    private static void readIntArr(JsonArray a, int[] out) {
        for (int i = 0; i < Math.min(a.size(), out.length); i++)
            out[i] = a.get(i).getAsInt();
    }
    private static void readDblArr(JsonArray a, double[] out) {
        for (int i = 0; i < Math.min(a.size(), out.length); i++)
            out[i] = a.get(i).getAsDouble();
    }
}
