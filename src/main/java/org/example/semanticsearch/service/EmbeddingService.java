package org.example.semanticsearch.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import org.springframework.stereotype.Service;

import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmbeddingService {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    public EmbeddingService() throws Exception {
        env = OrtEnvironment.getEnvironment();

        try (var modelStream = getClass().getClassLoader().getResourceAsStream("model.onnx")) {
            if (modelStream == null) throw new RuntimeException("model.onnx nu a fost gasit!");
            byte[] modelBytes = modelStream.readAllBytes();
            session = env.createSession(modelBytes, new OrtSession.SessionOptions());
        }

        try (var tokenizerStream = getClass().getClassLoader().getResourceAsStream("tokenizer.json")) {
            if (tokenizerStream == null) throw new RuntimeException("tokenizer.json nu a fost gasit!");
            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerStream, null);
        }
    }

    public float[] embed(String text) throws Exception {
        Encoding encoding = tokenizer.encode(text);

        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] tokenTypeIds = encoding.getTypeIds();

        int seqLen = inputIds.length;
        long[] shape = {1, seqLen};

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape));
        inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape));
        inputs.put("token_type_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape));

        OrtSession.Result result = session.run(inputs);
        float[][][] output = (float[][][]) result.get(0).getValue();

        // Mean pooling
        float[] embedding = new float[384];
        for (int j = 0; j < 384; j++) {
            float sum = 0;
            for (int k = 0; k < seqLen; k++) {
                sum += output[0][k][j];
            }
            embedding[j] = sum / seqLen;
        }

        // Normalizare L2
        float norm = 0;
        for (float v : embedding) norm += v * v;
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < embedding.length; i++) embedding[i] /= norm;

        return embedding;
    }
}