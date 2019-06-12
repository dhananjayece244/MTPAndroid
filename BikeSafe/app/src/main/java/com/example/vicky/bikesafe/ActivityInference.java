package com.example.vicky.bikesafe;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import static android.content.ContentValues.TAG;

public class ActivityInference {
    static {
        System.loadLibrary("tensorflow_inference");
    }
    private static ActivityInference activityInferenceInstance;
    private TensorFlowInferenceInterface inferenceInterface;

    private static final String MODEL_FILE = "file:///android_asset/model11_har.pb";
    private static final String INPUT_NODE = "input";
    private static final String[] OUTPUT_NODES = {"y_"};
    private static final String OUTPUT_NODE = "y_";
    private static final long[] INPUT_SIZE = {1, 300, 12};
    private static final int OUTPUT_SIZE = 5;
    private static AssetManager assetManager;



    public static ActivityInference getInstance(final Context context)
    {
        if (activityInferenceInstance == null)
        {
            activityInferenceInstance = new ActivityInference(context);
        }
        return activityInferenceInstance;
    }

    public ActivityInference(final Context context) {
        this.assetManager = context.getAssets();
        inferenceInterface = new TensorFlowInferenceInterface(assetManager, MODEL_FILE);
    }

    public float[] getActivityProb(float[] input_signal)
    {
        float[] result = new float[OUTPUT_SIZE];
        Log.d(TAG, "getActivityProb: " + input_signal.getClass() );
        inferenceInterface.feed(INPUT_NODE, input_signal, INPUT_SIZE);
        inferenceInterface.run(OUTPUT_NODES);
        inferenceInterface.fetch(OUTPUT_NODE,result);
        return result;
    }
}
