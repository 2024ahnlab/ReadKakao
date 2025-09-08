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

    private static TfLiteModelManager instance;
    private Interpreter tflite;
    private boolean isInitialized = false;

    private TfLiteModelManager() {}

    public static synchronized TfLiteModelManager getInstance(Context context) {
        if (instance == null) {
            instance = new TfLiteModelManager();
        }
        // initialize()를 여기서 호출하지 않고, MainActivity에서 명시적으로 호출하도록 변경
        return instance;
    }

    // --- [수정] 외부에서 호출할 수 있도록 public으로 변경 ---
    public void initialize(Context context) {
        if (isInitialized) return;
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, "distilkobert_fp16.tflite");
            Interpreter.Options options = new Interpreter.Options();
            tflite = new Interpreter(modelBuffer, options);
            isInitialized = true;
            Log.d(TAG, "TFLite 모델이 성공적으로 초기화되었습니다.");
        } catch (Exception e) {
            Log.e(TAG, "TFLite 모델 초기화 실패", e);
            isInitialized = false;
        }
    }

    // --- [추가] 모델이 초기화되었는지 확인하는 메소드 ---
    public boolean isInitialized() {
        return isInitialized;
    }

    private MappedByteBuffer loadModelFile(Context context, String modelFileName) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFileName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float[][] predict(int[][] inputIds, int[][] attentionMask) {
        if (!isInitialized || tflite == null) {
            Log.e(TAG, "predict 호출 시 모델이 초기화되지 않은 상태입니다.");
            return null;
        }

        Object[] inputs = {inputIds, attentionMask};
        float[][] output = new float[1][2];
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, output);

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
            isInitialized = false;
            // instance = null; // 싱글턴 인스턴스를 여기서 null로 만들지 않음
        }
    }
}
