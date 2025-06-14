package com.readkakaotalk.app.model;

import android.content.Context;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

public class TorchModelManager {
    private static final String TAG = "TorchModelManager";
    private Module model;
    private String modelFile;

    public TorchModelManager(String modelFileName) {
        this.modelFile = modelFileName;
    }

    public void loadModel(Context context) {
        try {
            model = Module.load(assetFilePath(context, modelFile));
            Log.d(TAG, "모델 로드 완료: " + modelFile);
        } catch (Exception e) {
            Log.e(TAG, "모델 로드 실패", e);
        }
    }

    public float[] predict(float[] input) {
        Tensor inputTensor = Tensor.fromBlob(input, new long[]{1, input.length});
        float[] output = model.forward(IValue.from(inputTensor)).toTensor().getDataAsFloatArray();
        return output;
    }

    private String assetFilePath(Context context, String assetName) throws Exception {
        java.io.File file = new java.io.File(context.getFilesDir(), assetName);
        if (!file.exists()) {
            java.io.InputStream is = context.getAssets().open(assetName);
            java.io.FileOutputStream os = new java.io.FileOutputStream(file);
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
            os.flush(); os.close(); is.close();
        }
        return file.getAbsolutePath();
    }
}
