package com.ai.fabric.runtime.documents;

import ai.fabric.document.DocumentReadOptions;
import java.util.List;
import java.util.HashSet;

/** Customer choices bounded by execution limits, never by inferred document meaning. */
public record DocumentPreparation(Processing processing, EvaluationOptions evaluationOptions, List<Question> questions) {
    public DocumentPreparation {
        processing = processing == null ? Processing.defaults() : processing;
        evaluationOptions = evaluationOptions == null ? EvaluationOptions.defaults() : evaluationOptions;
        questions = questions == null ? List.of() : List.copyOf(questions);
        if (questions.size() > 20 || new HashSet<>(questions.stream().map(Question::id).toList()).size() != questions.size())
            throw new IllegalArgumentException("Use at most 20 uniquely identified evaluation questions.");
    }
    public static DocumentPreparation defaults() { return new DocumentPreparation(null, null, null); }
    public record Processing(int chunkTokens, int overlapTokens, int minChunkChars, int minEmbedChars,
        DocumentReadOptions.OcrMode ocrMode, String ocrLanguage, int ocrDpi) {
        public Processing {
            if (chunkTokens < 100 || chunkTokens > 800 || overlapTokens < 0 || overlapTokens > chunkTokens / 2
                || minChunkChars < 0 || minChunkChars > chunkTokens || minEmbedChars < 0 || minEmbedChars > minChunkChars
                || ocrMode == null || ocrLanguage == null || !ocrLanguage.matches("[a-z]{3}(\\+[a-z]{3}){0,3}")
                || ocrDpi < 72 || ocrDpi > 300)
                throw new IllegalArgumentException("Chunk size 100..800, overlap up to half a chunk, valid minimum lengths and installed OCR language are required.");
        }
        public static Processing defaults() { return new Processing(800, 0, 200, 5, DocumentReadOptions.OcrMode.AUTO, "eng", 150); }
    }
    public record EvaluationOptions(int maxQuestions, int maxDocuments, int maxContextCharacters,
        double similarityThreshold, int answerMaxTokens) {
        public EvaluationOptions {
            if (maxQuestions < 1 || maxQuestions > 20 || maxDocuments < 1 || maxDocuments > 10
                || maxContextCharacters < 1000 || maxContextCharacters > 16000 || !Double.isFinite(similarityThreshold)
                || similarityThreshold < 0 || similarityThreshold > 1 || answerMaxTokens < 128 || answerMaxTokens > 1500)
                throw new IllegalArgumentException("Evaluation requires finite question, retrieval, context and answer limits.");
        }
        public static EvaluationOptions defaults() { return new EvaluationOptions(5, 5, 8000, 0.0, 700); }
    }
    public record Question(String id, String question, String referenceAnswer, boolean reviewed) {
        public Question {
            if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}") || question == null || question.isBlank() || question.length() > 1000
                || referenceAnswer != null && referenceAnswer.length() > 4000)
                throw new IllegalArgumentException("Each test needs an ID, a question up to 1000 characters and an optional reference up to 4000 characters.");
            referenceAnswer = referenceAnswer == null ? "" : referenceAnswer;
        }
    }
}
