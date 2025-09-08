package com.readkakaotalk.app.model;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class TfLiteModelManager {
    private static final String TAG = "TfLiteModelManager";
    private Interpreter tflite;
    private String modelFile;

    public TfLiteModelManager(String modelFileName) {
        this.modelFile = modelFileName;
    }

    public void loadModel(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, modelFile);
            Interpreter.Options options = new Interpreter.Options();
            tflite = new Interpreter(modelBuffer, options);
            Log.d(TAG, "TFLite 모델 로드 완료: " + modelFile);
        } catch (Exception e) {
            Log.e(TAG, "TFLite 모델 로드 실패", e);
            throw new RuntimeException("Failed to load TFLite model", e);
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelFileName) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFileName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * [최종 수정] 2개의 정수 배열을 입력받아 추론을 실행합니다.
     * @param inputIds 토큰화된 문장 배열 (예: [1][64])
     * @param attentionMask 어텐션 마스크 배열 (예: [1][64])
     * @return 모델의 추론 결과 (예: [1][2])
     */
    public float[][] predict(int[][] inputIds, int[][] attentionMask) {
        if (tflite == null) {
            Log.e(TAG, "모델이 로드되지 않았습니다.");
            return null;
        }

        // 1. 입력 데이터를 Map 형태로 준비합니다.
        Object[] inputs = {inputIds, attentionMask};

        // 2. 출력 데이터를 담을 Map을 준비합니다.
        float[][] output = new float[1][2]; // 모델의 출력 형태 [1, 2]에 맞게 수정
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, output);

        // 3. 추론 실행
        try {
            tflite.runForMultipleInputsOutputs(inputs, outputs);
        } catch (Exception e) {
            Log.e(TAG, "추론 중 오류 발생", e);
            return null;
        }

        return (float[][]) outputs.get(0);
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}
