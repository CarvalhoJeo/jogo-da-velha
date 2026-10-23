package br.unisinos.velha.client.ui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Folha de estilo embutida (data URI), para não depender de arquivo externo. */
final class Styles {

    private static final String RAW = """
            .root { -fx-font-size: 14px; -fx-background: #f6f7f9; }
            .title { -fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #1f2937; }
            .subtitle { -fx-font-size: 16px; -fx-text-fill: #4b5563; }
            .card { -fx-background-color: white; -fx-background-radius: 10; -fx-padding: 18;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 2); }
            .error { -fx-text-fill: #b91c1c; }
            .info { -fx-text-fill: #047857; }
            .status { -fx-font-size: 18px; -fx-text-fill: #1f2937; }
            .cell { -fx-font-size: 44px; -fx-font-weight: bold; -fx-background-color: white;
                    -fx-border-color: #d1d5db; -fx-border-width: 1; -fx-background-radius: 0; }
            .cell:hover { -fx-background-color: #eef2ff; }
            .cell-x { -fx-text-fill: #2563eb; }
            .cell-o { -fx-text-fill: #dc2626; }
            .cell-win { -fx-background-color: #fef08a; }
            .primary { -fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; }
            .danger { -fx-background-color: #dc2626; -fx-text-fill: white; }
            """;

    static final String CSS = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(RAW.getBytes(StandardCharsets.UTF_8));

    private Styles() {
    }
}
