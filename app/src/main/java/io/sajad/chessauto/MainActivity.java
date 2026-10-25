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
    private LinearLayout internalPanel,externalPanel;
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
        t.setTextColor(Color.rgb(225,237,240));
        if (heading) t.setTypeface(null, 1);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(7), dp(6), dp(7));
        return t;
    }

    private Button button(String label, LinearLayout parent) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(cardBackground(0xff152e38,0xff08202a,11,0xff226259));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1f);
        lp.setMargins(dp(3),dp(3),dp(3),dp(3));
        parent.addView(b,lp);
        return b;
    }

    private android.graphics.drawable.GradientDrawable cardBackground(int c1,int c2,int radius,int edge) {
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{c1,c2});
        d.setCornerRadius(dp(radius));d.setStroke(dp(1),edge);return d;
    }
    private LinearLayout section(LinearLayout root){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(8),dp(12),dp(8),dp(12));
        p.setBackground(cardBackground(0xff092333,0xff031423,15,0xff234c47));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(9),0,dp(9));
        root.addView(p,lp);return p;
    }
    private void buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(15),dp(12),dp(25));
        root.setBackground(cardBackground(0xff081823,0xff010912,0,0xff081823));
        scroll.addView(root);
        setContentView(scroll);
        TextView knight=text("♞",66,true);
        knight.setTextColor(0xffe4bc69);
        root.addView(knight,new LinearLayout.LayoutParams(-1,dp(103)));
        TextView heading=text("Chess AI Auto Player 3.1",23,true);
        heading.setTextColor(0xffe6cb83);root.addView(heading);
        root.addView(text("بوت شطرنج قوي ضد الكمبيوتر فقط · Stockfish 19",13,false));
        LinearLayout modes=new LinearLayout(this);root.addView(modes);
        Button inside=button("♟ الشطرنج الداخلي",modes);
        Button outside=button("▣ مشاركة الشاشة",modes);
        internalPanel=section(root);
        externalPanel=section(root);
        externalPanel.setVisibility(View.GONE);
        inside.setOnClickListener(v->{internalPanel.setVisibility(View.VISIBLE);externalPanel.setVisibility(View.GONE);});
        outside.setOnClickListener(v->{internalPanel.setVisibility(View.GONE);externalPanel.setVisibility(View.VISIBLE);});
        internalPanel.addView(text("شطرنج تلقائي · ضد الكمبيوتر",16,true));
        status=text("",16,true);internalPanel.addView(status);
        boardView=new ChessBoardView(this);
        int width=getResources().getDisplayMetrics().widthPixels-dp(44);
        internalPanel.addView(boardView,new LinearLayout.LayoutParams(-1,width));
        lastMove=text("",14,false);internalPanel.addView(lastMove);
        LinearLayout actions=new LinearLayout(this);internalPanel.addView(actions);
        start=button("▶ ابدأ اللعب",actions);
        Button restart=button("↻ مباراة جديدة",actions);
        start.setOnClickListener(v->{running=!running;generation++;render();if(running)scheduleMove(generation);});
        restart.setOnClickListener(v->reset(null));
        LinearLayout options=new LinearLayout(this);internalPanel.addView(options);
        colorButton=button("البوت: الأبيض",options);
        levelButton=button("الخصم: سهل",options);
        colorButton.setOnClickListener(v->{botWhite=!botWhite;reset(null);});
        levelButton.setOnClickListener(v->{levelIndex=(levelIndex+1)%levels.length;reset(null);});
        internalPanel.addView(text("وضعية FEN اختيارية",14,true));
        fenInput=new EditText(this);
        fenInput.setHint("FEN للمباراة الداخلية");
        fenInput.setHintTextColor(0xff9cabb2);
        fenInput.setTextColor(Color.WHITE);
        fenInput.setTextSize(13);
        internalPanel.addView(fenInput,new LinearLayout.LayoutParams(-1,dp(62)));
        Button load=new Button(this);load.setText("تحميل وضعية FEN");
        load.setTextColor(Color.WHITE);load.setBackground(cardBackground(0xff1b695b,0xff105043,12,0xff21ac80));
        internalPanel.addView(load,new LinearLayout.LayoutParams(-1,dp(55)));
        load.setOnClickListener(v->{String fen=fenInput.getText().toString().trim();if(!fen.isEmpty())reset(fen);});
        externalPanel.addView(text("▣ مشاركة الشاشة مع تطبيق شطرنج خارجي",17,true));
        externalPanel.addView(text("اللعب ضد الكمبيوتر فقط · المعايرة مطلوبة · الإيقاف تلقائي عند فقد التزامن",13,false));
        addExternalControls(externalPanel);
        root.addView(text("نسخة v3 تجريبية — نجاح البناء لا يثبت التوافق مع جميع ألعاب الشطرنج",12,false));
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
    private boolean whiteAtBottomExternal = true;
    private boolean useDragExternal;
    private int thinkMsExternal = 350;
    private android.widget.CheckBox offlineOnly;
    private TextView externalStatus;
    private EditText externalFen;

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
        LinearLayout settings=new LinearLayout(this);root.addView(settings);
        Button orientation=button("أسفل الرقعة: الأبيض",settings);
        Button drag=button("الحركة: نقر",settings);
        orientation.setOnClickListener(v->{
            whiteAtBottomExternal=!whiteAtBottomExternal;
            orientation.setText(whiteAtBottomExternal?"أسفل الرقعة: الأبيض":"أسفل الرقعة: الأسود");
        });
        drag.setOnClickListener(v->{
            useDragExternal=!useDragExternal;
            drag.setText(useDragExternal?"الحركة: سحب":"الحركة: نقر");
        });
        LinearLayout speed=new LinearLayout(this);root.addView(speed);
        Button think=button("زمن التفكير: 350ms",speed);
        think.setOnClickListener(v->{
            int[] times={180,350,750,1250};int j=0;
            while(j<times.length && times[j]!=thinkMsExternal)j++;
            thinkMsExternal=times[(j+1)%times.length];
            think.setText("زمن التفكير: "+thinkMsExternal+"ms");
        });
        offlineOnly = new android.widget.CheckBox(this);
        offlineOnly.setTextColor(Color.WHITE);
        offlineOnly.setText("أؤكد أن المباراة ضد كمبيوتر فقط، وليست ضد لاعب بشري أو عبر الإنترنت");
        offlineOnly.setTextSize(13);
        root.addView(offlineOnly);
        externalFen = new EditText(this);
        externalFen.setSingleLine(false);
        externalFen.setTextSize(13);
        externalFen.setMinLines(2);
        externalFen.setTextColor(Color.WHITE);
        externalFen.setHintTextColor(Color.GRAY);
        externalFen.setHint("اختياري: ألصق FEN إذا المباراة بدأت بالفعل. اتركه فارغاً فقط للمباراة الجديدة");
        root.addView(externalFen, new LinearLayout.LayoutParams(-1, dp(77)));
        Button enable = new Button(this);
        enable.setText("1 — تفعيل خدمة اللمس والتأشير");
        enable.setAllCaps(false);
        root.addView(enable);
        enable.setOnClickListener(v -> {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        // Independent tests: users can check the accessibility overlay and
        // Android gesture injection BEFORE sharing the screen or calibrating.
        TextView diagnosticsTitle=text("اختبار الصلاحيات قبل تشغيل الشطرنج",16,true);
        root.addView(diagnosticsTitle);
        Button visualCheck=new Button(this);
        visualCheck.setText("أ — اختبار التأشير الأسود والأخضر");
        visualCheck.setAllCaps(false);
        root.addView(visualCheck);
        visualCheck.setOnClickListener(v -> {
            OverlayAccessibility service=OverlayAccessibility.active;
            if(service==null) {
                externalStatus.setText("خدمة إمكانية الوصول غير متصلة. فعّلها من إعدادات الهاتف؛ وإذا ظهرت إعدادات مقيّدة راجع السماح بالتطبيق من صفحة معلوماته.");
                return;
            }
            service.showDiagnosticMarkers();
            externalStatus.setText("راقب الشاشة: سيظهر اختبار المربعين الأسود والأخضر والشريط العائم، مستقلّين عن الشطرنج.");
            ui.postDelayed(() -> externalStatus.setText(service.overlayDiagnostics()),850);
        });
        Button testTarget=new Button(this);
        testTarget.setText("هدف اختبار اللمس — لا تضغطه يدوياً");
        testTarget.setAllCaps(false);
        testTarget.setBackground(cardBackground(0xff17533d,0xff0b392b,12,0xff22ba77));
        testTarget.setTextColor(Color.WHITE);
        root.addView(testTarget,new LinearLayout.LayoutParams(-1,dp(55)));
        testTarget.setOnClickListener(v -> {
            externalStatus.setText("✓ وصلت اللمسة إلى الهدف داخل التطبيق. خدمة اللمس قادرة على إرسال النقرات.");
        });
        Button gestureCheck=new Button(this);
        gestureCheck.setText("ب — إرسال لمسة اختبار إلى الزر الأخضر أعلاه");
        gestureCheck.setAllCaps(false);
        root.addView(gestureCheck);
        gestureCheck.setOnClickListener(v -> {
            OverlayAccessibility service=OverlayAccessibility.active;
            if(service==null) {
                externalStatus.setText("خدمة إمكانية الوصول غير مفعلة. لا يمكن اختبار اللمس.");
                return;
            }
            int[] xy=new int[2];
            testTarget.getLocationOnScreen(xy);
            if(testTarget.getWidth()<10 || testTarget.getHeight()<10) {
                externalStatus.setText("أظهر زر الهدف الأخضر بالكامل ثم أعد الاختبار.");
                return;
            }
            externalStatus.setText("جاري إرسال لمسة إلى الهدف الأخضر داخل التطبيق...");
            service.testTapOnOwnApp(xy[0]+testTarget.getWidth()/2f,xy[1]+testTarget.getHeight()/2f);
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
                android.content.Intent consent;
                if(android.os.Build.VERSION.SDK_INT>=34) {
                    consent=manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay());
                } else consent=manager.createScreenCaptureIntent();
                startActivityForResult(consent,991);
            } catch (Exception e) {
                externalStatus.setText("تعذر طلب المشاركة: " + e.getMessage());
            }
        });
        externalStatus = text("ابدأ باختبار التأشير واللمس حتى تتأكد من إذن إمكانية الوصول، ثم شغّل مشاركة الشاشة.", 13, false);
        root.addView(externalStatus);
        Button captureCheck=new Button(this);
        captureCheck.setText("ج — قراءة التشخيص الحقيقي لمشاركة الشاشة");
        captureCheck.setAllCaps(false);
        root.addView(captureCheck);
        captureCheck.setOnClickListener(v -> {
            String state=getSharedPreferences("chess_capture_diagnostics",MODE_PRIVATE)
                .getString("status","لم تصل أي رسالة من خدمة مشاركة الشاشة حتى الآن");
            String overlay=(OverlayAccessibility.active==null)
                ?"إمكانية الوصول: غير متصلة"
                :OverlayAccessibility.active.overlayDiagnostics();
            externalStatus.setText("حالة الالتقاط: "+state+"\n"+overlay);
        });
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
        running = false; generation++; render();
        ExternalController.begin(this, externalWhite, externalAuto,
            externalFen == null ? "" : externalFen.getText().toString(),
            whiteAtBottomExternal,thinkMsExternal,useDragExternal);
        android.content.Intent capture = new android.content.Intent(this, ExternalCaptureService.class);
        capture.putExtra(ExternalCaptureService.EXTRA_RESULT, resultCode);
        capture.putExtra(ExternalCaptureService.EXTRA_DATA, data);
        try {
            getSharedPreferences("chess_capture_diagnostics",MODE_PRIVATE)
                .edit().putString("status","جاري تشغيل خدمة الالتقاط...").apply();
            startForegroundService(capture);
            if (externalStatus != null) externalStatus.setText("تم إرسال طلب بدء المشاركة. تحقق من ظهور لوحة الشطرنج العائمة، ثم افتح لعبة الكمبيوتر بنفسك وعاير الرقعة.");
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
