package io.sajad.chessauto;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/** Pure display board; the chesslib Board is authoritative for legal moves. */
public final class ChessBoardView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";
    private boolean whiteBottom = true;

    public ChessBoardView(Context context) { super(context); }

    public void setPosition(String fullFen, boolean whiteAtBottom) {
        fen = fullFen.split(" ")[0];
        whiteBottom = whiteAtBottom;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        final float d = Math.min(getWidth(), getHeight());
        final float cell = d / 8f;
        final int light = Color.rgb(239, 216, 181);
        final int dark = Color.rgb(172, 125, 88);
        String[] ranks = fen.split("/");
        char[][] board = new char[8][8];
        if (ranks.length != 8) return;
        for (int r = 0; r < 8; r++) {
            int col = 0;
            for (char ch : ranks[r].toCharArray()) {
                if (Character.isDigit(ch)) col += ch - '0';
                else if (col < 8) board[r][col++] = ch;
            }
        }
        for (int r = 0; r < 8; r++) for (int col = 0; col < 8; col++) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(((r + col) & 1) == 0 ? light : dark);
            c.drawRect(col * cell, r * cell, (col + 1) * cell, (r + 1) * cell, p);
            int sourceRow = whiteBottom ? r : 7 - r;
            int sourceCol = whiteBottom ? col : 7 - col;
            char ch = board[sourceRow][sourceCol];
            String piece = unicodePiece(ch);
            if (piece.length() > 0) {
                p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(cell * 0.83f);
                p.setColor(Character.isUpperCase(ch) ? Color.rgb(248, 248, 239) : Color.rgb(25, 25, 25));
                p.setStyle(Paint.Style.FILL);
                p.setShadowLayer(1.8f, 1f, 1f, Character.isUpperCase(ch) ? Color.BLACK : Color.WHITE);
                c.drawText(piece, (col + 0.5f) * cell,
                    (r + 0.5f) * cell - (p.ascent() + p.descent()) / 2f, p);
                p.clearShadowLayer();
            }
        }
    }

    private static String unicodePiece(char ch) {
        switch (ch) {
            case 'K': return "♔"; case 'Q': return "♕"; case 'R': return "♖";
            case 'B': return "♗"; case 'N': return "♘"; case 'P': return "♙";
            case 'k': return "♚"; case 'q': return "♛"; case 'r': return "♜";
            case 'b': return "♝"; case 'n': return "♞"; case 'p': return "♟";
            default: return "";
        }
    }
}
