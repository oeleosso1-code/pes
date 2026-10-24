package io.sajad.chessauto;

import android.content.Context;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/** Executes the Stockfish UCI process extracted into Android's nativeLibraryDir. */
public final class StockfishEngine implements Closeable {
    private final Context context;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;

    public StockfishEngine(Context context) { this.context = context.getApplicationContext(); }

    private void send(String command) throws Exception {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private void until(String prefix) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(prefix)) return;
        }
        throw new IllegalStateException("Stockfish terminated while waiting for " + prefix);
    }

    private void ensureStarted() throws Exception {
        if (process != null && process.isAlive()) return;
        File executable = new File(context.getApplicationInfo().nativeLibraryDir, "libstockfish.so");
        if (!executable.isFile()) throw new IllegalStateException("Stockfish binary missing: " + executable);
        process = new ProcessBuilder(executable.getAbsolutePath()).redirectErrorStream(true).start();
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        send("uci");
        until("uciok");
        send("setoption name Threads value 1");
        send("setoption name Hash value 32");
        send("isready");
        until("readyok");
    }

    /** @return legal UCI candidate, e.g. e2e4 or a7a8q; caller verifies legality. */
    public synchronized String bestMove(String fen, int thinkingMs, boolean limitStrength, int elo)
        throws Exception {
        ensureStarted();
        send("setoption name UCI_LimitStrength value " + (limitStrength ? "true" : "false"));
        if (limitStrength) send("setoption name UCI_Elo value " + elo);
        send("isready");
        until("readyok");
        send("position fen " + fen);
        send("go movetime " + Math.max(100, thinkingMs));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("bestmove ")) {
                String[] parts = line.split("\\s+");
                if (parts.length > 1 && parts[1].matches("[a-h][1-8][a-h][1-8][qrbn]?")) {
                    return parts[1];
                }
                throw new IllegalStateException("No valid move from engine: " + line);
            }
        }
        throw new IllegalStateException("Stockfish terminated unexpectedly");
    }

    @Override public synchronized void close() {
        if (process != null) {
            try { send("quit"); } catch (Exception ignored) {}
            process.destroy();
            process = null;
        }
    }
}
