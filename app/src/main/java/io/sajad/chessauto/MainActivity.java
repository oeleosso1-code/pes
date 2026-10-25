package io.sajad.chessauto;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full autonomous engine-vs-engine chess: bot plays for you against computer. */
public final class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private Board board = new Board();
    private StockfishEngine engine;
    private ChessBoardView boardView;
    private TextView status;
    private TextView lastMove;
    private Button start;
    private Button colorButton;
    private Button levelButton;
    private EditText fenInput;
    private boolean running = false;
    private int generation = 0;
    private int plies = 0;
    private boolean botWhite = true;
    private int levelIndex = 0;
    private final int[] opponentElo = {1350, 1600, 2000};
    private final String[] levels = {"سهل", "متوسط", "صعب"};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        engine = new StockfishEngine(this);
        buildUi();
        render();
    }

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    private TextView text(String content, int size, boolean heading) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(31, 41, 55));
        if (heading) t.setTypeface(null, 1);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(7), dp(6), dp(7));
        return t;
    }

    private Button button(String label, LinearLayout parent) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        parent.addView(b, new LinearLayout.LayoutParams(0, dp(57), 1f));
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(22));
        root.setBackgroundColor(Color.rgb(252, 249, 242));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("♟ Chess AI Auto Player", 23, true));
        root.addView(text("البوت يلعب تلقائياً بدلاً منك ضد خصم كمبيوتر داخل هذا التطبيق — دون إنترنت", 14, false));

        status = text("", 16, true);
        root.addView(status);
        boardView = new ChessBoardView(this);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        root.addView(boardView, new LinearLayout.LayoutParams(-1, screenWidth - dp(20)));
        lastMove = text("", 14, false);
        root.addView(lastMove);

        LinearLayout line1 = new LinearLayout(this);
        root.addView(line1);
        start = button("▶ ابدأ اللعب", line1);
        Button reset = button("↻ مباراة جديدة", line1);
        start.setOnClickListener(v -> {
            running = !running;
            generation++;
            render();
            if (running) scheduleMove(generation);
        });
        reset.setOnClickListener(v -> reset(null));

        LinearLayout line2 = new LinearLayout(this);
        root.addView(line2);
        colorButton = button("البوت: الأبيض", line2);
        levelButton = button("الخصم: سهل", line2);
        colorButton.setOnClickListener(v -> { botWhite = !botWhite; reset(null); });
        levelButton.setOnClickListener(v -> { levelIndex = (levelIndex + 1) % levels.length; reset(null); });

        addExternalControls(root);

        root.addView(text("وضعية مخصصة (FEN) — اختيارية", 14, true));
        fenInput = new EditText(this);
        fenInput.setHint("الصق FEN إذا أردت بدء مباراة من وضعية محددة");
        fenInput.setTextSize(13);
        fenInput.setSingleLine(false);
        fenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(fenInput, new LinearLayout.LayoutParams(-1, dp(76)));
        Button load = new Button(this);
        load.setText("تحميل الوضعية");
        load.setAllCaps(false);
        root.addView(load);
        load.setOnClickListener(v -> {
            String fen = fenInput.getText().toString().trim();
            if (!fen.isEmpty()) reset(fen);
        });
        root.addView(text("هذا الإصدار يتحكم برقعة الشطرنج الموجودة داخل التطبيق نفسه. لا يدّعي قراءة أو التحكم بأي تطبيق شطرنج خارجي.", 12, false));
    }

    private void reset(String fen) {
        running = false;
        generation++;
        try {
            Board b = new Board();
            if (fen != null) b.loadFromFen(fen);
            if (b.legalMoves().isEmpty()) {
                board = b;
            } else {
                board = b;
            }
            plies = 0;
            lastMove.setText("مباراة جديدة");
            render();
        } catch (Exception ex) {
            status.setText("صيغة FEN غير صحيحة: " + ex.getMessage());
        }
    }

    private boolean gameOver() {
        return board.isMated() || board.isStaleMate() || board.isDraw();
    }

    private void render() {
        boardView.setPosition(board.getFen(), botWhite);
        colorButton.setText(botWhite ? "البوت: الأبيض" : "البوت: الأسود");
        levelButton.setText("الخصم: " + levels[levelIndex]);
        start.setText(running ? "Ⅱ إيقاف مؤقت" : "▶ ابدأ اللعب");
        if (board.isMated()) {
            running = false;
            start.setText("انتهت المباراة");
            status.setText("كش مات — الفائز " + (board.getSideToMove() == Side.WHITE ? "الأسود" : "الأبيض"));
        } else if (board.isStaleMate() || board.isDraw()) {
            running = false;
            start.setText("انتهت المباراة");
            status.setText("تعادل");
        } else {
            boolean myTurn = (board.getSideToMove() == Side.WHITE) == botWhite;
            status.setText((running ? "جار اللعب — " : "متوقف — ") +
                (myTurn ? "دور البوت القوي" : "دور الكمبيوتر") + " | النقلات: " + plies);
        }
    }

    private void scheduleMove(final int token) {
        if (!running || gameOver()) { render(); return; }
        final String fen = board.getFen();
        final boolean strongest = (board.getSideToMove() == Side.WHITE) == botWhite;
        final int elo = opponentElo[levelIndex];
        status.setText(strongest ? "محرك Stockfish يحلل أفضل نقلة..." : "الخصم الكمبيوتر يفكر...");
        work.submit(() -> {
            try {
                String uci = engine.bestMove(fen, strongest ? 900 : 250, !strongest, elo);
                ui.post(() -> {
                    if (!running || generation != token || !board.getFen().equals(fen)) return;
                    try {
                        Move move = new Move(uci, board.getSideToMove());
                        if (!board.isMoveLegal(move, true)) {
                            throw new IllegalStateException("محرك الشطرنج أعطى نقلة غير قانونية: " + uci);
                        }
                        board.doMove(move);
                        plies++;
                        lastMove.setText((strongest ? "البوت" : "الكمبيوتر") + ": " + uci);
                        render();
                        if (running && !gameOver()) ui.postDelayed(() -> scheduleMove(token), 400);
                    } catch (Exception e) {
                        running = false; render();
                        status.setText("خطأ في تنفيذ النقلة: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                ui.post(() -> {
                    if (generation != token) return;
                    running = false; render();
                    status.setText("تعذر تشغيل Stockfish: " + e.getMessage());
                });
            }
        });
    }


    // External modes deliberately require user consent for both accessibility and
    // screen capture. These modes are intended for offline computer games only.
    private boolean externalAuto = true;
    private boolean externalWhite = true;
    private android.widget.CheckBox offlineOnly;
    private TextView externalStatus;

    private void addExternalControls(LinearLayout root) {
        root.addView(text("♟ التحكم بألعاب الشطرنج الخارجية (ضد الكمبيوتر فقط)", 19, true));
        root.addView(text("اختر بين اللمس التلقائي أو تأشير القطعة بالأسود ومربع الهدف بالأخضر. ابدأ مباراة جديدة بوضع البداية القياسي.", 13, false));
        LinearLayout row = new LinearLayout(this);
        root.addView(row);
        Button mode = button("الوضع: لمس تلقائي", row);
        Button color = button("اللون: الأبيض", row);
        mode.setOnClickListener(v -> {
            externalAuto = !externalAuto;
            mode.setText(externalAuto ? "الوضع: لمس تلقائي" : "الوضع: إرشاد ملوّن");
        });
        color.setOnClickListener(v -> {
            externalWhite = !externalWhite;
            color.setText(externalWhite ? "اللون: الأبيض" : "اللون: الأسود");
        });
        offlineOnly = new android.widget.CheckBox(this);
        offlineOnly.setText("أؤكد أن المباراة ضد كمبيوتر فقط، وليست ضد لاعب بشري أو عبر الإنترنت");
        offlineOnly.setTextSize(13);
        root.addView(offlineOnly);
        Button enable = new Button(this);
        enable.setText("1 — تفعيل خدمة اللمس والتأشير");
        enable.setAllCaps(false);
        root.addView(enable);
        enable.setOnClickListener(v -> {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        Button share = new Button(this);
        share.setText("2 — مشاركة الشاشة وتشغيل المساعد الخارجي");
        share.setAllCaps(false);
        root.addView(share);
        share.setOnClickListener(v -> {
            if (!offlineOnly.isChecked()) {
                externalStatus.setText("يجب تأكيد نمط الكمبيوتر أولاً.");
                return;
            }
            if (OverlayAccessibility.active == null) {
                externalStatus.setText("فعّل Chess AI Auto Player ضمن إعدادات إمكانية الوصول ثم ارجع هنا.");
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            try {
                android.media.projection.MediaProjectionManager manager =
                    (android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                externalStatus.setText("وافق على مشاركة الشاشة بالكامل، ثم افتح لعبة الشطرنج.");
                startActivityForResult(manager.createScreenCaptureIntent(), 991);
            } catch (Exception e) {
                externalStatus.setText("تعذر طلب المشاركة: " + e.getMessage());
            }
        });
        externalStatus = text("بعد بدء المشاركة افتح اللعبة واضغط «تحديد الرقعة» من الشريط العائم.", 13, false);
        root.addView(externalStatus);
        Button end = new Button(this);
        end.setAllCaps(false);
        end.setText("إيقاف المساعد الخارجي");
        root.addView(end);
        end.setOnClickListener(v -> {
            if (ExternalController.active != null) ExternalController.active.stop();
            stopService(new android.content.Intent(this,ExternalCaptureService.class));
            externalStatus.setText("تم إيقاف المساعد الخارجي");
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 991) return;
        if (resultCode != RESULT_OK || data == null) {
            if (externalStatus != null) externalStatus.setText("تم إلغاء مشاركة الشاشة");
            return;
        }
        ExternalController.begin(this, externalWhite, externalAuto);
        android.content.Intent capture = new android.content.Intent(this, ExternalCaptureService.class);
        capture.putExtra(ExternalCaptureService.EXTRA_RESULT, resultCode);
        capture.putExtra(ExternalCaptureService.EXTRA_DATA, data);
        try {
            startForegroundService(capture);
            if (externalStatus != null) externalStatus.setText("المشاركة بدأت: افتح لعبة كمبيوتر جديدة ثم عاير الرقعة.");
            ui.postDelayed(() -> {
                android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                home.addCategory(android.content.Intent.CATEGORY_HOME);
                home.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);
            }, 700);
        } catch (Exception e) {
            if (externalStatus != null) externalStatus.setText("فشل بدء المشاركة: " + e.getMessage());
        }
    }

    @Override protected void onDestroy() {
        running = false;
        generation++;
        work.shutdownNow();
        new Thread(() -> engine.close()).start();
        super.onDestroy();
    }
}
