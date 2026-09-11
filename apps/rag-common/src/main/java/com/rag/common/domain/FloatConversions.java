package com.rag.common.domain;

import java.util.ArrayList;
import java.util.List;

public final class FloatConversions {

    private FloatConversions() {}

    public static List<Float> toFloatList(float[] values) {
        var result = new ArrayList<Float>(values.length);
        for (float v : values) result.add(v);
        return result;
    }

    public static float[] toFloatArray(List<Float> values) {
        var result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }
}
