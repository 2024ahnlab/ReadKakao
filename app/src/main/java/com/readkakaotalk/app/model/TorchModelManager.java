package com.readkakaotalk.app.model;

import android.content.Context;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class TorchModelManager {
    private Module model;

    public void loadModel(Context context) {
        try {
            // 모델을 assets 폴더에서 내부 저장소로 복사
            File modelFile = new File(context.getFilesDir(), "model.pt");
            if (!modelFile.exists()) {
                try (InputStream is = context.getAssets().open("model.pt");
                     FileOutputStream fos = new FileOutputStream(modelFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, length);
                    }
                }
            }

            model = Module.load(modelFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public float[] predict(float[] inputData) {
        // 예: 입력 길이는 10으로 가정
        Tensor inputTensor = Tensor.fromBlob(inputData, new long[]{1, inputData.length});
        Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();
        return outputTensor.getDataAsFloatArray();
    }
}
