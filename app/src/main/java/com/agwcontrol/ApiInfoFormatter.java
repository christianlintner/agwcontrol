package com.agwcontrol;

import java.util.List;

public class ApiInfoFormatter {

    public String format(String serverLabel, List<ApiInfo> apis) {
        if (apis.isEmpty()) {
            return "Keine APIs gefunden.";
        }

        int nameWidth    = Math.max(4, apis.stream().mapToInt(a -> a.getName()   .length()).max().orElse(0));
        int versionWidth = Math.max(7, apis.stream().mapToInt(a -> nullSafe(a.getVersion()).length()).max().orElse(0));
        int typeWidth    = Math.max(3, apis.stream().mapToInt(a -> nullSafe(a.getType())   .length()).max().orElse(0));

        String rowFmt = "  %-" + nameWidth + "s  %-" + versionWidth + "s  %-" + typeWidth + "s  %s%n";
        int lineWidth = 2 + nameWidth + 2 + versionWidth + 2 + typeWidth + 2 + 7;
        String separator = "─".repeat(lineWidth);

        StringBuilder sb = new StringBuilder();
        sb.append("APIs auf ").append(serverLabel).append("\n");
        sb.append(separator).append("\n");
        sb.append(String.format(rowFmt, "Name", "Version", "Typ", "Status"));
        sb.append(separator).append("\n");

        for (ApiInfo api : apis) {
            sb.append(String.format(rowFmt,
                    api.getName(),
                    nullSafe(api.getVersion()),
                    nullSafe(api.getType()),
                    api.isActive() ? "AKTIV" : "INAKTIV"));
        }

        sb.append(separator).append("\n");
        sb.append("  ").append(apis.size()).append(apis.size() == 1 ? " API gefunden" : " APIs gefunden");
        return sb.toString();
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
