package io.sajad.chessauto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Session controller for external OFFLINE computer chess.
 * This tracker intentionally assumes a fresh standard starting position. It tracks
 * visual differences against legally reachable positions, not general-purpose OCR.
 * When a move cannot be identified unambiguously, it does not touch the other app.
 */
public final class ExternalController {
    static ExternalController active;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final StockfishEngine engine;
    private Board board = new Board();
    private float[] reference;
    private RectF bounds;
    private boolean whiteBottom;
    private final boolean botWhite;
    private final boolean automatic;
    private boolean stopped;
    private boolean thinking;
    private String proposed;
    private String lastCandidate;
    private int candidateRepeats;
    private long lastAutoAction;

    private ExternalController(Context c, boolean white, boolean auto) {
        context = c.getApplicationContext();
        engine = new StockfishEngine(context);
        botWhite = white;
        automatic = auto;
        whiteBottom = white;
    }

    public static void begin(Context context, boolean botWhite, boolean automatic) {
        if (active != null) active.stop();
        active = new ExternalController(context, botWhite, automatic);
        if (OverlayAccessibility.active != null) OverlayAccessibility.active.attach();
    }

    public static void notifyStatus(String message) {
        if (OverlayAccessibility.active != null) OverlayAccessibility.active.setStatus(message);
    }

    public synchronized void calibrate(RectF rectangle) {
        if (rectangle.width() < 120 || rectangle.height() < 120) {
            notifyStatus("خطأ: مساحة الرقعة صغيرة");
            return;
        }
        float d = Math.min(rectangle.width(), rectangle.height());
        bounds = new RectF(rectangle.left, rectangle.top, rectangle.left + d, rectangle.top + d);
        resetPosition();
        notifyStatus("الرقعة محددة. تأكد أنها ببداية المباراة، ثم ابدأ ضد الكمبيوتر.");
    }

    public synchronized RectF getBounds() {
        return bounds == null ? null : new RectF(bounds);
    }

    public boolean isWhiteBottom() { return whiteBottom; }

    public synchronized void resetPosition() {
        board = new Board();
        reference = null;
        proposed = null;
        thinking = false;
        lastCandidate = null;
        candidateRepeats = 0;
        lastAutoAction = 0;
        if (OverlayAccessibility.active != null) OverlayAccessibility.active.showMove(null, null, bounds, whiteBottom);
        notifyStatus("أعد المباراة للوضع الابتدائي ثم اختر العب ضد الكمبيوتر");
    }

    public synchronized void onFrame(Bitmap image) {
        try {
            if (stopped || bounds == null) return;
            if (image.getWidth() < bounds.right || image.getHeight() < bounds.bottom) {
                notifyStatus("حجم الشاشة تغير. أعد تحديد الرقعة");
                bounds = null;
                return;
            }
            float[] current = sample(image, bounds);
            if (reference == null) {
                reference = current;
                maybeThink();
                return;
            }
            if (board.isMated() || board.isStaleMate() || board.isDraw()) {
                notifyStatus("انتهت المباراة");
                return;
            }
            float[] changes = differences(reference, current);
            int count = 0;
            for (float d : changes) if (d > 24) count++;
            if (count < 2) {
                lastCandidate = null;
                candidateRepeats = 0;
                return;
            }
            if (count > 9) {
                lastCandidate = null;
                candidateRepeats = 0;
                notifyStatus("تغيرت مساحة كبيرة. ثبّت الرقعة ولا تحركها.");
                return;
            }
            Move move = detect(board.legalMoves(), changes);
            if (move == null) {
                lastCandidate = null;
                candidateRepeats = 0;
                return;
            }
            String uci = move.toString().toLowerCase();
            if (proposed != null && !proposed.equals(uci)) {
                // Only acknowledge the exact move the bot proposed on its own turn.
                boolean ourTurn = (board.getSideToMove() == Side.WHITE) == botWhite;
                if (ourTurn) return;
            }
            if (!uci.equals(lastCandidate)) {
                lastCandidate = uci;
                candidateRepeats = 1;
                return;
            }
            if (++candidateRepeats < 3) return;
            if (!board.isMoveLegal(move, true)) return;
            board.doMove(move);
            reference = current;
            lastCandidate = null;
            candidateRepeats = 0;
            proposed = null;
            if (OverlayAccessibility.active != null)
                OverlayAccessibility.active.showMove(null, null, bounds, whiteBottom);
            notifyStatus("تم التعرف على النقلة: " + uci);
            maybeThink();
        } catch (Exception e) {
            notifyStatus("تعذر قراءة النقلة: " + e.getMessage());
        }
    }

    private void maybeThink() {
        if (thinking || proposed != null || stopped || bounds == null) return;
        if ((board.getSideToMove() == Side.WHITE) != botWhite) {
            notifyStatus("الكمبيوتر يلعب؛ نراقب نقلته...");
            return;
        }
        if (board.isMated() || board.isStaleMate() || board.isDraw()) return;
        thinking = true;
        final String fen = board.getFen();
        notifyStatus("Stockfish يحلل النقلة...");
        worker.submit(() -> {
            try {
                String move = engine.bestMove(fen, 650, false, 3000);
                main.post(() -> proposeMove(move, fen));
            } catch (Exception e) {
                main.post(() -> {
                    thinking = false;
                    notifyStatus("خطأ في المحرك: " + e.getMessage());
                });
            }
        });
    }

    private synchronized void proposeMove(String uci, String originalFen) {
        thinking = false;
        if (stopped || bounds == null || !board.getFen().equals(originalFen)) return;
        Move move = new Move(uci, board.getSideToMove());
        if (!board.isMoveLegal(move, true)) {
            notifyStatus("خطأ: نقلة غير قانونية من المحرك");
            return;
        }
        proposed = uci;
        if (OverlayAccessibility.active != null) {
            OverlayAccessibility.active.showMove(
                move.getFrom().toString().toLowerCase(),
                move.getTo().toString().toLowerCase(), bounds, whiteBottom);
        }
        if (!automatic) {
            notifyStatus("الإرشاد: الأسود = القطعة، الأخضر = المكان: " + uci);
        } else {
            notifyStatus("تنفيذ تلقائي: " + uci);
            lastAutoAction = System.currentTimeMillis();
            if (OverlayAccessibility.active != null)
                OverlayAccessibility.active.executeMove(uci, bounds, whiteBottom);
            else notifyStatus("خدمة اللمس غير مفعّلة؛ فعّل إمكانية الوصول");
        }
    }

    public synchronized void gestureFailed() {
        notifyStatus("فشل تنفيذ النقلة. جرّب المعايرة أو وضع الإرشاد.");
    }

    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        worker.shutdownNow();
        new Thread(() -> engine.close()).start();
        if (OverlayAccessibility.active != null) OverlayAccessibility.active.detach();
        if (active == this) active = null;
    }

    private static float[] sample(Bitmap image, RectF r) {
        float[] out = new float[64 * 27]; // 9 sample points x RGB per square
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            int k = (row * 8 + col) * 27;
            for (int sy = 0; sy < 3; sy++) for (int sx = 0; sx < 3; sx++) {
                float px = r.left + (col + 0.32f + sx * 0.18f) * r.width() / 8f;
                float py = r.top + (row + 0.32f + sy * 0.18f) * r.height() / 8f;
                int rgb = image.getPixel(Math.min(image.getWidth()-1, Math.max(0, (int) px)),
                    Math.min(image.getHeight()-1, Math.max(0, (int) py)));
                int q = k + (sy * 3 + sx) * 3;
                out[q] = (rgb >> 16) & 255;
                out[q+1] = (rgb >> 8) & 255;
                out[q+2] = rgb & 255;
            }
        }
        return out;
    }

    private static float[] differences(float[] before, float[] after) {
        float[] d = new float[64];
        for (int i = 0; i < 64; i++) {
            float n = 0;
            for (int k = 0; k < 27; k++)
                n += Math.abs(before[i*27+k] - after[i*27+k]);
            d[i] = n / 27f;
        }
        return d;
    }

    private int viewIndex(String square) {
        int file = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '1';
        return whiteBottom ? (7 - rank)*8 + file : rank*8 + (7 - file);
    }

    private Move detect(List<Move> moves, float[] changes) {
        Move best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        float nextScore = Float.NEGATIVE_INFINITY;
        int totalChanged = 0;
        for (float value : changes) if (value > 20) totalChanged++;
        if (totalChanged < 2 || totalChanged > 8) return null;
        for (Move m : moves) {
            String from = m.getFrom().toString().toLowerCase();
            String to = m.getTo().toString().toLowerCase();
            int a = viewIndex(from), b = viewIndex(to);
            if (changes[a] < 13 || changes[b] < 13) continue;
            boolean[] expected = new boolean[64];
            expected[a] = true;
            expected[b] = true;
            if (Math.abs(from.charAt(0) - to.charAt(0)) == 2
                && board.getPiece(m.getFrom()).toString().toUpperCase().contains("KING")) {
                char rank = from.charAt(1);
                if (to.charAt(0) == 'g') {
                    expected[viewIndex("h"+rank)] = true;
                    expected[viewIndex("f"+rank)] = true;
                } else if (to.charAt(0) == 'c') {
                    expected[viewIndex("a"+rank)] = true;
                    expected[viewIndex("d"+rank)] = true;
                }
            }
            float inside = 0, outside = 0;
            int expectedCount = 0;
            for (int i = 0; i < 64; i++) {
                if (expected[i]) {
                    inside += changes[i];
                    expectedCount++;
                } else if (changes[i] > 12) {
                    outside += changes[i] - 12;
                }
            }
            float score = inside - outside * 2.8f - expectedCount * 2f;
            if (score > bestScore) {
                nextScore = bestScore;
                bestScore = score;
                best = m;
            } else if (score > nextScore) nextScore = score;
        }
        if (best == null || bestScore < 24) return null;
        // Promotion choices are optically indistinguishable before a piece is recognized;
        // prefer queen only when fully unambiguous; other promotions require recalibration.
        if (bestScore - nextScore < 1f) {
            if (best.getPromotion() == null ||
                !best.getPromotion().toString().toUpperCase().contains("QUEEN"))
                return null;
        }
        return best;
    }
}
