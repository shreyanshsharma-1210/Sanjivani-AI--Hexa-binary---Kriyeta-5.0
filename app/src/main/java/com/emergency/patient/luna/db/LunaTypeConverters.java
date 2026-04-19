package com.emergency.patient.luna.db;

import androidx.room.TypeConverter;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public class LunaTypeConverters {

    @TypeConverter
    public static String fromStringList(List<String> list) {
        if (list == null) return "[]";
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        return arr.toString();
    }

    @TypeConverter
    public static List<String> toStringList(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) result.add(arr.getString(i));
        } catch (JSONException ignored) {}
        return result;
    }
}
